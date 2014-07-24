(ns monte-raft.node.state-worker-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [monte-raft.node.state-worker :as state]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.leader-worker :refer [publish-state]]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]
            [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go chan >!! <!!]]
            [monte-raft.test.worker-macros :refer :all]))

(comment (deftest-worker test-state-updates
   (testing "state-run should be updating transient-state on updates"
     (let [pub-remote "inproc://state-updates"
           stop-chan (chan)             ; To stop the state thread
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
                 (worker/start (state/state-worker
                                 (assoc (node-config)
                                   :leader-publish-binding
                                   (:publish-binding (node-config)))))]
             (socket/send-str-timeout state-update-pub socket/default-timeout
               (json/write-str expected-state))
             (is (= (<!! finished-chan) :exiting))
             (is (= @node-state/transient-state
                   (json/write-str expected-state))))))))))

(deftest-worker test-state-worker-updates
  (testing "state-worker should directly update transient"
    (let [expected-state {"name" "jake"}
          expected-encoded (json/write-str expected-state)
          worker-config (assoc (node-config)
                          :leader-publish-remote ((node-config) :publish-binding))]
      (with-open [update-pub (doto (zmq/socket socket/ctx :pub)
                               (zmq/bind ((node-config) :publish-binding)))]
        (binding [node-state/transient-state (atom nil)]
          (let [running-worker (worker/start
                                 (state/state-worker worker-config))]
            (wait-do 100
              (publish-state update-pub expected-encoded)
              (worker/signal-terminate (get-in worker-config [:kill-codes :state]))
              (is (= (<!! running-worker) :terminated))
              (is (= @node-state/transient-state
                    expected-encoded)))))))))
