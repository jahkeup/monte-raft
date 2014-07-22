(ns monte-raft.node.worker-test
  (:require [clojure.test :refer :all]
            [monte-raft.test.worker-macros :refer :all]
            [clojure.core.async :refer [<! <!! >! >!! go]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.state :as node-state]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [taoensso.timbre :as log]))


(binding [node-state/node-id "testing-node"]
  (deftest-worker test-worker-termination
    (let [running-worker (worker/start
                           (worker/until-worker-terminate :test-worker
                             (Thread/sleep 100))
                           :terminated)
          received? (atom false)
          value-received (on-message-reset! running-worker received? true)]
      (Thread/sleep 100)
      (worker/signal-terminate :test-worker)
      (Thread/sleep 50)
      (is received?)
      (is (= (<!! value-received) :terminated)))))
