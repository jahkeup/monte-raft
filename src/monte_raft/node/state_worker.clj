(ns monte-raft.node.state-worker
  (:require [monte-raft.node.worker :as worker]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]))


(defn state-worker
  "Go thead designed to run in the background until a message is sent
  over the stop-chan channel. "
  [{:keys [update-socket stop-chan check-period]}]
  (worker/until-worker-terminate :state
    ;; Potential problems here in the future if the windows don't align..
    (if-let [new-state (socket/receive-str-timeout
                         update-socket check-period)]
      ;; Blindly set transient state to the received state.
      (reset! node-state/transient-state new-state)))
  :exiting)
