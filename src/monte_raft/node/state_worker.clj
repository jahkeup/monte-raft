(ns monte-raft.node.state-worker
  (:require [monte-raft.node.worker :as worker]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

(defn maybe-get-update [subsocket]
  (socket/receive-str-timeout
    subsocket))

(defn state-worker
  "Go thead designed to run in the background until a message is sent
  over the stop-chan channel. "
  [{:keys [leader-publish-remote kill-codes] :as worker-options}]
  (log/infof "Starting state-worker on '%s'" node-state/node-id)
  (with-open [update-socket (doto (zmq/socket socket/ctx :sub)
                              (zmq/connect leader-publish-remote)
                              (zmq/subscribe ""))]
    (log/tracef "state-worker started.")
    (worker/until-worker-terminate (kill-codes :state)
      (log/trace "waiting for state update...")
      ;; Potential problems here in the future if the windows don't align..
      (if-let [new-state (maybe-get-update update-socket)]
        (do (reset! node-state/transient-state new-state)
            (log/tracef "State updated: '%s'" new-state)))))
  (log/tracef "state-worker exiting.")
  :terminated)
