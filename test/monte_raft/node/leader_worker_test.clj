(ns monte-raft.node.leader-worker-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!!]]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.leader-worker :as leader]
            [monte-raft.test.worker-macros :refer :all]
            [monte-raft.node.state :as node-state]))

(def pub-binding "Testing publishing address" "inproc://test-state-pub")

(deftest-worker test-leader-starts
  (let [running-worker (worker/start
                         (leader/leader-worker "node-leader" (assoc (node-config)
                                                               :publish-binding pub-binding)))]
    (wait-do 100
      (worker/signal-terminate (get-in (node-config) [:kill-codes :leader]))
      (is (= (<!! running-worker) :terminated)))))
