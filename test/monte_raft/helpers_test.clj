(ns monte-raft.helpers-test
  (:require [clojure.test :refer :all]
            [monte-raft.test.worker-macros :refer :all]
            [taoensso.timbre :as log]
            [monte-raft.node.state :as node-state]))

(deftest-worker test-deftest-worker
  (is (= node-state/node-id "test-node")))
