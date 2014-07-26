(ns monte-raft.node.state-worker
  (:require [monte-raft.node.worker :as worker]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.leader-worker :as leader]
            [clojure.core.async :as async :refer [>!!]]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

(defn maybe-get-update [subsocket timeout]
  (socket/receive-str-timeout
    subsocket timeout))

(defn state-worker
  "Go thead designed to run in the background until a message is sent
  over the stop-chan channel. "
  ([worker-config]
     (state-worker worker-config nil))
  ([{:keys [node-id state] :as worker-config} started-chan]
     (log/infof "Starting state-worker on '%s'" node-state/node-id)
     (try
       (let [leader-publish-remote (leader/leader-remote worker-config)
             {:keys [transient]} state]
         (log/tracef "State worker (%s) connecting to '%s' for updates."
           node-id leader-publish-remote)
         (with-open [update-socket (doto (zmq/socket socket/ctx :sub)
                                     (zmq/connect leader-publish-remote)
                                     (zmq/subscribe ""))]
           (log/tracef "State worker (%s) started and connected to '%s'."
             node-id leader-publish-remote)
           (and started-chan (>!! started-chan :state))
           (worker/until-worker-terminate worker-config :state
             (log/tracef "State worker (%s) waiting for state update..." node-id)
             ;; Potential problems here in the future if the windows don't align..
             (if-let [new-state (maybe-get-update update-socket (worker-config :timeout))]
               (do (reset! transient new-state)
                   (log/tracef "State updated: '%s'" new-state))))))
       (log/tracef "state-worker exiting.")
       :terminated
       (catch Throwable e (do (clojure.stacktrace/print-cause-trace e)
                              (log/errorf "State worker (%s) encountered an error." node-id)
                              (and started-chan (>!! started-chan :state-fail)))))))
