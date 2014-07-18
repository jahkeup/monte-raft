(ns monte-raft.node.state-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go chan >!! <!!]]))

(deftest test-state-updates
  (testing "state-run should be updating transient-state on updates"
    (let [pub-remote "inproc://state-updates"
          stop-chan (chan) ; To stop the state thread
          expected-state {"name" "jake"}]
      (with-open
          [state-update-pub (socket/make-state-update-publisher socket/ctx
                              pub-remote)
           state-update-sub (socket/make-state-update-subscriber socket/ctx
                              pub-remote)]
        (zmq/subscribe state-update-sub "")
        (binding [node-state/transient-state (atom nil)]
          ;; Start a state management "thread"
          (let [finished-chan
                (go (node-state/state-worker
                      {:update-socket state-update-sub
                       :stop-chan stop-chan
                       :check-period 1000}))]
            (socket/send-str-timeout state-update-pub socket/default-timeout
              (json/write-str expected-state))
            (>!! stop-chan true)
            (is (= (<!! finished-chan) :exiting))
            (is (= @node-state/transient-state
                  (json/write-str expected-state)))))))))


