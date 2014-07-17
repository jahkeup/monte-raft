(ns monte-raft.node.control
  (:require [taoensso.timbre :as log]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.worker-comm :refer :all]
            [monte-raft.node.macros :refer [until-message-from]]))

(defn handle-message
  "Inbound message dispatcher, msg should be raw message receieved
  from remotes"
  [reply-socket msg]
  (log/tracef "Handling message: '%s'" msg)
  (if (msgs/valid-cmd? msg)
    (let [cmd (msgs/to-command-fmt msg)]
      (if (contains? handlers/cmd-handlers cmd)
        (let [handler-func (get handlers/cmd-handlers cmd)]
          (handler-func reply-socket))))))

(defn control-worker
  "Go thread control worker to process incoming messages, must be
  started prior to other workers

  node-id: the node's identifier

  control-binding: the binding of the control socket"
  [control-binding term-chan]
  (log/tracef "Starting control worker: listening on %s" control-binding)
  (binding [worker-comm-sock (do (log/trace "Establishing worker communication socket")
                                 (make-worker-comm-sock))
            socket/control-socket (do (log/trace "Binding control socket")
                                      (socket/make-control-listener control-binding))]
    (log/trace "Beginning control worker loop")
    (until-message-from term-chan
      ;; Control loop
      (log/trace "Gonna sleep...")
      (Thread/sleep 1)
      (log/trace "control-worker looping."))
    (log/trace "Control socket is preparing to exit.")
    (doall (for [w '(:leader :state)]
             (do (log/tracef "Sending worker '%s' kill message" (name w))
                 (send-worker-message w :terminate))))))

