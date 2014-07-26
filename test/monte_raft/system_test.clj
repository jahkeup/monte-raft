(ns monte-raft.system-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [monte-raft.test.worker-macros :refer :all]
            [monte-raft.system :as system]
            [monte-raft.node.worker :as worker]))


(deftest test-cluster-simple-start
  (testing "Cluster basically* starts."
    (let [cluster-config {:node-id :cluster}]
      (worker/with-comm-sock cluster-config
        (with-messages-logged cluster-config
          (let [running-cluster (worker/start (system/start-system))]
            (wait-do 2000
              (system/stop-system)
              (wait-do 200
                (is (= (<!! running-cluster) :system-terminated))))))))))

