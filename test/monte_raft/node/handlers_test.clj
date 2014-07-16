(ns monte-raft.node.handlers-test
  (:require [clojure.test :refer :all]
            [zeromq.zmq :as zmq]
            [monte-raft.utils :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.handlers :as handlers]))


(deftest test-ping-handler
  (testing "Ping handler should respond with a pong"
    (let [called? (atom false)
          expected-msg :pong
          catcher (fn [s t msg] (if (= msg expected-msg)
                                  (reset! called? true)))]
      (with-redefs [socket/send-str-timeout catcher]
        (handlers/handle-ping nil)
        (is @called?)))))

(deftest-pending test-elect-handler
  (testing "Elect handler"))


(deftest test-confirm-handler)


(deftest test-commit-handler)
