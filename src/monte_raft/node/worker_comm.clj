(ns monte-raft.node.worker-comm
  (:require [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]))

(def ^:dynamic worker-comm-sock nil)

(defn worker-comm-remote []
  (format "inproc://%s-worker-comm" node-state/node-id))

(defn make-worker-comm-sock [sock-binding]
  (doto (zmq/socket socket/ctx :pub)
    (zmq/bind (worker-comm-remote))))

(defmacro until-worker-terminate [worker-type & body]
  `(let [sock# (doto (zmq/socket socket/ctx :sub)
                 (zmq/connect worker-comm-remote))]
     (zmq/subscribe sock# (name ~worker-type))
     (until-receive-from sock# ~@body)))

(defn send-worker-message [worker-type msg]
  (zmq/send-str worker-comm-sock
    (format "%s %s" (name worker-type) msg)))

