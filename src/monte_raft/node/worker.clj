(ns monte-raft.node.worker
  (:require [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [clojure.core.async :refer [go]]
            [zeromq.zmq :as zmq]))

(def ^:dynamic comm-sock nil)

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
       (zmq/bind (comm-remote)))))

(defmacro until-worker-terminate
  "Loop until a message arrives on a subscribed worker socket"
  [worker-type & body]
  `(let [sock# (doto (zmq/socket socket/ctx :sub)
                 (zmq/connect comm-remote))]
     (zmq/subscribe sock# (name ~worker-type))
     (until-receive-from sock# ~@body)))

(defn send-message
  "Send targeted worker message, will send to all `worker-type'
  subscribers and if subscribed with until-worker-terminate this will
  cause remote worker to cease looping."
  [worker-type msg]
  (zmq/send-str comm-sock
    (format "%s %s" (name worker-type) msg)))

(defmacro start
  "Start worker function, may do some worker tracking here in the
  future, for now its mostly a wrapper around go"
  [& worker-call]
  `(go ~@worker-call))
