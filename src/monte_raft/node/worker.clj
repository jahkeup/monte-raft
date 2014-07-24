(ns monte-raft.node.worker
  (:require [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [clojure.core.async :refer [go]]
            [zeromq.zmq :as zmq]
            [taoensso.timbre :as log]))

(def ^:dynamic comm-sock nil)

(defmacro log-error-throw "Log an error and throw it"
  [msg]
  `(do (log/error ~msg)
      (throw (Exception. ~msg))))

(defn comm-remote
  "Generate the inproc control worker communication socket address
  based on the current binding of the node-id" []
  (format "inproc://%s-worker-comm" node-state/node-id))

(defn make-comm-sock
  "Create a worker communication socket"
  ([]
     (make-comm-sock (comm-remote)))
  ([sock-binding]
     (doto (zmq/socket socket/ctx :pub)
       (zmq/bind sock-binding))))

(defmacro until-worker-terminate
  "Loop until a message arrives on a subscribed worker socket"
  [worker-type & body]
  `(let [sock# (doto (zmq/socket socket/ctx :sub)
                 (zmq/connect (comm-remote))
                 (zmq/subscribe (name ~worker-type)))]
     (log/tracef "Worker subscribing to '%s' for messages" (name ~worker-type))
     (until-receive-from sock# ~@body)))

(defn comm-sock-logger [remote]
  (with-open [sock (doto (zmq/socket socket/ctx :sub)
                     (zmq/connect remote)
                     (zmq/subscribe ""))]
    (until-worker-terminate :logger
      (if-let [recvd (socket/receive-str-timeout sock)]
        (log/tracef "Worker bus message: '%s'" recvd)))))

(defn send-message
  "Send targeted worker message, will send to all `worker-type'
  subscribers and if subscribed with until-worker-terminate this will
  cause remote worker to cease looping."
  [worker-type msg]
  (zmq/send-str comm-sock
    (format "%s %s" (name worker-type) msg)))

(defn signal-terminate
  "Send a terminate message to subscribing worker-type"
  ([worker-type]
     (log/tracef "Sending terminate for '%s' workers" (name worker-type))
     (send-message worker-type :terminate))
  ([worker-type worker-config]
     (signal-terminate (get-in worker-config [:kill-codes worker-type]
                         worker-type))))

(defmacro start
  "Start worker function, may do some worker tracking here in the
  future, for now its mostly a wrapper around go"
  [& worker-call]
  `(go ~@worker-call))

