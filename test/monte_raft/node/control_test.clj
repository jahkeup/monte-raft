(ns monte-raft.node.control-test
  (:require [clojure.test :refer :all]
            [zeromq.zmq :as zmq]
            [monte-raft.utils :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.control-worker :as control]
            [monte-raft.node.handlers :as handlers]))



(deftest test-handle-message
  (testing "Calls handler when matches message"
    (let [called? (atom false)
          override-handlers {:ping (fn [_] (reset! called? true))}]
      (with-redefs [handlers/cmd-handlers override-handlers]
        (control/handle-message nil "ping")
        (is called?)))))

