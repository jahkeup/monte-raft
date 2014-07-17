(ns monte-raft.node.control
  (:require [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.worker-comm :refer :all]))

(defn handle-message
  "Inbound message dispatcher"
  [reply-socket msg]
  (if (msgs/valid-cmd? msg)
    (let [cmd (msgs/to-command-fmt msg)]
      (if (contains? handlers/cmd-handlers cmd)
        (let [handler-func (get handlers/cmd-handlers cmd)]
          (handler-func reply-socket))))))

(defn control-worker
  "Go thread control worker to process incoming messages

  node-id: the node's identifier

  control-binding: the binding of the control socket"
  [control-binding]
  (binding [worker-comm-sock (make-worker-comm-sock)]))
