(ns monte-raft.node.control
  (:require [taoensso.timbre :as log]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.worker :as worker]
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

(defn maybe-handle-message-from
  "Handler delegation, dies on a timeout"
  [control-socket]
  (if-let [message (socket/receive-str-timeout control-socket)]
    (handle-message control-socket message)
    :timeout))

(defn control-worker
  "Go thread control worker to process incoming messages, must be
  started prior to other workers

  control-binding: the binding of the control socket

  term-chan: channel, when received on, will exit"
  [control-binding term-chan]
  (log/tracef "Starting control worker: listening on %s" control-binding)
  (with-open [worker-comm-sock (worker/make-comm-sock)
              control-socket (socket/make-control-listener control-binding)]
    (binding [socket/control-socket control-socket]
      (log/trace "Beginning control worker loop")
      (until-message-from term-chan
        ;; Control loop
        (maybe-handle-message-from control-socket))
      (log/trace "Control socket is preparing to exit.")
      (doall (for [w '(:leader :state)]
               (do (log/tracef "Sending worker '%s' kill message" (name w))
                   (worker/send-message w :terminate)))))))

;; Okay this is working but the REPL isn't having it.
;;
;; (require '[clojure.core.async :as async])
;; (def term-chan (async/chan))
;; (reset! log/level-atom :trace)
;; (log/trace "Running")
;; (def running-worker (async/go (control-worker "inproc://control-socket"
;;                                 term-chan)))
;; (Thread/sleep 10000)
;; (async/>!! term-chan true)
;; (log/trace "Done")
