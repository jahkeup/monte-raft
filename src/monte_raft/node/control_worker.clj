(ns monte-raft.node.control-worker
  (:require [taoensso.timbre :as log]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.macros :refer [until-message-from]]
            [monte-raft.node.leader-worker :as leader]
            [monte-raft.node.state-worker :as state]
            [zeromq.zmq :as zmq]))

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
  started prior to other workers. Will listen for commands/leader
  messages on 'control-binding'. Can be terminated with
  monte-raft.node.worker/signal-terminate, note: workers now listen on
  a node-config'd kill-code, so use that."

  [{:keys [kill-codes publish-binding control-binding] :as worker-config}]
  (log/tracef "Starting control worker: listening on %s" control-binding)
  (with-open [control-socket (doto (zmq/socket socket/ctx :rep)
                               (zmq/bind control-binding))]
    (log/trace "Control worker started.")
    (if (leader/is-leader?)
      (worker/start (leader/leader-worker
                      leader/leader-id
                      worker-config)))
    (let [leader-remote (or (worker-config :leader-publish-remote)
                          (leader/leader-remote))
          state-worker-config (assoc worker-config :leader-publish-remote
                                     leader-remote)]
      (if leader-remote
        (worker/start (state/state-worker
                        state-worker-config))
        (worker/log-error-throw
          "Control cannot determine leader publishing remote or not given. ")))
    (worker/until-worker-terminate (kill-codes :control)
      ;; Control loop
      (maybe-handle-message-from control-socket))
    (log/trace "Control socket is preparing to exit.")
    (doall (for [w '(:leader :state)]
             (do (log/tracef "Control worker sending '%s' kill message using %s"
                   (name w) (kill-codes w))
                 (worker/signal-terminate (kill-codes w)))))
    (log/trace "Control worker exiting.")
    :terminated))

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
