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
                         (leader/leader-worker (assoc (node-config)
                                                 :publish-binding pub-binding)))]
    (wait-do 100
      (worker/signal-terminate (get-in (node-config) [:kill-codes :leader]))
      (is (= (<!! running-worker) :terminated)))))

(deftest-worker test-leader-remote
  (testing "can resolve leader-remote"
    (is (not (nil? node-state/node-id)) "should be present")
    (is (not (nil? @node-state/cluster)) "cluster should be set")
    (is (= 1 (count (keys @node-state/cluster))) "cluster should be test")
    (is (= (leader/leader-remote (node-config))
          (:publish-binding (node-config))))))

