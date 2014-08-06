(ns monte-raft.node.worker
  (:require [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [clojure.core.async :refer [go]]
            [zeromq.zmq :as zmq]
            [taoensso.timbre :as log]))

(def ^:dynamic comm-sock
  "Socket to be used for publishing worker messages" nil)

(def ^:dynamic last-bound-comm-sock (atom nil))

(defmacro log-error-throw "Log an error and throw it"
  [msg]
  `(do (log/error ~msg)
      (throw (Exception. ~msg))))

(defn comm-remote
  "Generate the inproc control worker communication socket address
  based on the current binding of the node-id (or pull from a
  node-config map passed"
  ([node-id]
     (comm-remote node-id false))
  ([node-id force]
     (if (not (and (not force) comm-sock @last-bound-comm-sock))
       (let [conn-string "inproc://%s-worker-comm"
             conn-remote (cond
                           (map? node-id) (format conn-string (name (:node-id node-id)))
                           :default (format conn-string (name node-id)))]
         conn-remote)
       @last-bound-comm-sock)))

(defn make-comm-sock
  "Create a worker communication socket, refuses to create double
  sockets. Useful for override a worker 'context'."
  [{:keys [node-id]}]
  (if comm-sock comm-sock
      (do (log/tracef "Worker creating new worker pub '%s'" (comm-remote node-id))
          (reset! last-bound-comm-sock (comm-remote node-id))
          (doto (zmq/socket socket/ctx :pub)
            (zmq/bind (comm-remote node-id))))))

(defmacro with-comm-sock
  "Bind comm-sock and run"
  [node-config & body]
  `(binding [last-bound-comm-sock (if @last-bound-comm-sock
                                    last-bound-comm-sock
                                      (atom nil))]
     (binding [comm-sock (make-comm-sock ~node-config)]
       (log/tracef "Last comm sock: '%s'" @last-bound-comm-sock)
       ~@body)))

(defmacro until-worker-terminate
  "Loop until a message arrives on a subscribed worker socket"
  [worker-config worker-type & body]
  `(do
     (let [worker-sub-name# (get-in ~worker-config [:kill-codes ~worker-type] ~worker-type)
           _# (log/tracef "Worker (%s) connecting to '%s' as worker comm"
                (name worker-sub-name#) (comm-remote ~worker-config))
           sock# (doto (zmq/socket socket/ctx :sub)
                   (zmq/connect (comm-remote ~worker-config))
                   (zmq/subscribe (name worker-sub-name#)))]
       (log/tracef "Worker subscribing to '%s' for messages" (name worker-sub-name#))
       (until-receive-from sock#
         ~@body))))

(defn comm-sock-logger [remote]
  (with-open [sock (doto (zmq/socket socket/ctx :sub)
                     (zmq/connect remote)
                     (zmq/subscribe ""))]
    (try
      (until-worker-terminate {:kill-codes {:logger :logger}} :logger
        (if-let [recvd (socket/receive-str-timeout sock)]
          (log/tracef "Worker bus message: '%s'" recvd)))
      (catch Throwable e (clojure.stacktrace/print-cause-trace e)))))

(defn send-message
  "Send targeted worker message, will send to all `worker-type'
  subscribers and if subscribed with until-worker-terminate this will
  cause remote worker to cease looping."
  [worker-type msg]
  (if (not comm-sock) (throw (Exception. "Worker communication socket has not been opened.")))
  (if (not (and worker-type msg))
    (throw (Exception. "Cannot send empty message with empty worker-type")))
  (try
      (zmq/send-str comm-sock
        (format "%s %s" (name worker-type) msg))
      (catch Throwable e (do (clojure.stacktrace/print-cause-trace e)
                             (throw e)))))

(defmacro signal-terminate
  "Send a terminate message to subscribing worker-type"
  ([worker-type]
     `(try
        (do (log/tracef "Sending terminate for '%s' workers" (name ~worker-type))
            (send-message ~worker-type :terminate))
        (catch Throwable e# (do (log/errorf "Error occurred during worker termination")
                                (clojure.stacktrace/print-cause-trace e#)
                                (throw e#)))))
  ([worker-type worker-config]
     `(if-let [kill-code# (get-in ~worker-config [:kill-codes ~worker-type])]
        (signal-terminate kill-code#)
        (throw (Exception. (format "Cannot retrieve kill-code for %s from node config %s"
                             ~worker-type
                             (with-out-str (clojure.pprint/pprint ~worker-config))))))))

(defmacro start
  "Start worker function, may do some worker tracking here in the
  future, for now its mostly a wrapper around go"
  [& worker-call]
  `(go ~@worker-call))

(defn min-quorum
  "Returns minimum for quorum for x (int)"
  [x]
  (inc (long (/ x 2))))

(defn quorum?
  "Does x meet the quorum minimum? For the nodes in the cluster if mx
  not given."
  ([x]
     (let [mx (count (keys @node-state/cluster))]
       (quorum? x mx)))
  ([x mx]
     (let [q (min-quorum mx)]
       (and (>= x q) (<= x mx)))))

(quorum? 8 15)
