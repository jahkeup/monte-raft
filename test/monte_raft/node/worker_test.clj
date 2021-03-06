(ns monte-raft.node.worker-test
  (:require [clojure.test :refer :all]
            [monte-raft.test.worker-macros :refer :all]
            [clojure.core.async :refer [<! <!! >! >!! go]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [taoensso.timbre :as log]))

(deftest-worker test-worker-termination
  (let [worker-config (assoc-in (node-config) [:kill-codes :test-worker] :test-worker)
        running-worker (worker/start
                         (worker/until-worker-terminate
                           worker-config :test-worker
                           (Thread/sleep 100))
                         :terminated)
        received? (atom false)
        value-received (on-message-reset! running-worker received? true)]
    (wait-do 100
      (worker/signal-terminate :test-worker worker-config)
      (wait-do 50
        (is received?)
        (is (= (<!! value-received) :terminated))))))
