(ns monte-raft.node.control-worker-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!!]]
            [zeromq.zmq :as zmq]
            [monte-raft.utils :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.control-worker :as control]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.test.worker-macros :refer :all]))

(def control-binding "test node control socket binding" "inproc://test-node-control")

(deftest test-handle-message
  (testing "Calls handler when matches message"
    (let [called? (atom false)
          override-handlers {:ping (fn [_] (reset! called? true))}]
      (with-redefs [handlers/cmd-handlers override-handlers]
        (control/handle-message nil "ping")
        (is called?)))))

(deftest-worker test-control-worker-starts
  (with-messages-logged
    (let [running-worker (worker/start
                          (control/control-worker control-binding))]
     (wait-do 100
       (worker/signal-terminate :control)
       (Thread/sleep 2000)
       (worker/signal-terminate :control)
       (is (= (<!! running-worker) :terminated))))))