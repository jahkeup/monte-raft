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

  [{:keys [node-id kill-codes publish-binding control-binding] :as worker-config}]
  (log/tracef "Control worker (%s) starting with config: \n%s" node-id
    (with-out-str (clojure.pprint/pprint worker-config)))
  (log/tracef "Starting control worker (%s): listening on %s" node-id control-binding)
  (try
    (with-open [control-socket (doto (zmq/socket socket/ctx :rep)
                                 (zmq/bind control-binding))]
      (log/trace "Control worker started.")
      (if (leader/is-leader? worker-config)
        (do (worker/start (leader/leader-worker
                            worker-config))
            (Thread/sleep 10)))
      (if (leader/leader-remote worker-config)
        (worker/start (state/state-worker
                        worker-config))
        (worker/log-error-throw
          (format "Control worker (%s) cannot determine leader publishing remote or not given." node-id)))
      (worker/until-worker-terminate worker-config :control
        ;; Control loop
        (maybe-handle-message-from control-socket))
      (log/trace "Control socket is preparing to exit.")
      (doall (for [w '(:state :leader)]
               (do (log/tracef "Control worker (%s) sending '%s' kill message using %s"
                     node-id (name w) (kill-codes w))
                   (worker/signal-terminate (kill-codes w)))))
      (log/trace "Control worker exiting.")
      :terminated)
    (catch Throwable e (clojure.stacktrace/print-stack-trace e))))

