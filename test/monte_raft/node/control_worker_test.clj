(ns monte-raft.node.control-worker-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! chan]]
            [zeromq.zmq :as zmq]
            [monte-raft.utils :refer :all]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.control-worker :as control]
            [monte-raft.node.worker :as worker]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.test.worker-macros :refer :all]
            [taoensso.timbre :as log]))

(defn clear-state! [state-map]
  (doall
    (for [[k resetval] [[:transient nil] [:current nil]
                        [:term 0] [:confirmed false]
                        [:elected nil]]]
      (reset! (get state-map k) resetval))))

(defmacro testing-clean-state [state desc & body]
  `(testing ~desc
     (do (clear-state! ~state)
         ~@body)))

(defn send-recv-timeout [sock send]
  (socket/send-str-timeout sock send)
  (socket/receive-str-timeout sock))

(deftest test-handle-message
  (testing "Calls handler when matches message"
    (let [called? (atom false)
          override-handlers {:ping (fn [_ _] (reset! called? true))}]
      (with-redefs [handlers/cmd-handlers override-handlers]
        (control/handle-message nil "ping" {})
        (is called?)))))

(deftest-worker test-control-worker-starts
  (let [running-worker (worker/start
                         (control/control-worker (node-config)))]
    (wait-do 100
      (worker/signal-terminate :control (node-config))
      (is (= (<!! running-worker) :terminated)))))

(deftest-worker test-control-reactions
  (let [{:keys [state node-id control-binding kill-codes] :as worker-config} (node-config)
        {:keys [transient current term confirmed]} state
        log-val (fn [desc x] (log/tracef "Current value of '%s': '%s'" desc x))
        started-chan (chan 1)
        running-worker (worker/start (control/control-worker worker-config
                                       started-chan))]
    (log/tracef "Test will begin once worker has started.")
    (or (not (= (<!! started-chan) :control-fail))
      (throw (Exception. "Control did not start.")))
    (try
      (with-open [sock (doto (zmq/socket socket/ctx :req)
                         (zmq/connect control-binding))]
        (log/infof "Started control-worker (%s) for testing, will shutdown after.%s"
          (:control kill-codes) bar)
        (testing-clean-state state
          "Control can handle invalid messages"
          (let [proper-response "BAD COMMAND"
                command "GIVE ME THE ANSWER!"]
            (let [response (send-recv-timeout sock command)]
              (is (= proper-response response)))))
        (testing-clean-state state
          "Control confirms"
          (let [proper-response "CONFIRMED"
                inproper-response "UNCONFIRMED"]
            (testing "when it has state"
              (reset! transient "some-state")
              (if-let [response (send-recv-timeout sock "CONFIRM")]
                (is (= (clojure.string/upper-case response) "CONFIRMED") "confirms transient")))
            (testing "but when it doesn't have state, unconfirmed."
              (reset! transient nil)
              (let [response (send-recv-timeout sock "CONFIRM")]
                (is response "should have responded")
                (is (= response inproper-response) "shouldn't be successful.")))))
        (testing-clean-state state
          "Control should set current upon a COMMIT and"
          (let [command "COMMIT"
                proper-response "COMMITTED"
                inproper-response "UNCOMMITTED"]
            (testing-clean-state state
              "without state"
              (is (nil? @transient))
              (is (false? @confirmed))
              (let [response (send-recv-timeout sock command)]
                (is (= response inproper-response))))
            (testing-clean-state state
              "with some state, not confirmed"
              (reset! transient "some-state")
              (is (false? @confirmed))
              (let [response (send-recv-timeout sock command)]))
            (testing "respond in proper state"
              (reset! transient "some-state")
              (reset! confirmed true)
              (let [response (send-recv-timeout sock command)]
                (is response "should have responded")
                (is (= response proper-response))))))
        (testing-clean-state state
          "Control can respond to PING"
          (let [command "PING" proper-response "PONG"]
            (testing "with PONG"
              (let [response (send-recv-timeout sock command)]
                (is (= response proper-response) "responds correctly")))))
        (testing-clean-state state
          "Control can respond to TERM"
          (reset! term (inc (rand-int 10)))
          (let [command "TERM"
                proper-response (format "TERM %s" @term)]
            (let [response (send-recv-timeout sock "TERM")]
              (is (= response proper-response)))))
        (testing-clean-state state
          "Control can respond to ELECT :node-id"
          (let [node :n1
                command (format "ELECT %s" node)
                proper-response "VOTE"]
            (let [response (send-recv-timeout sock "ELECT :n1")]
              (is (= response proper-response))
              (is (= node @(get-in worker-config [:state :elected]))))
            (let [response (send-recv-timeout sock "ELECT :some-other-node")]
              (is (= response "NOVOTE"))
              (is (= node @(get-in worker-config [:state :elected]))))))
        (testing-clean-state state
          "Control can change leaders properly"
          (let [node :test-node
                command (format "FOLLOW %s" node)
                proper-response (format "FOLLOWING %s" node)]
            (let [response (send-recv-timeout sock command)]
              (is (= response proper-response))
              (is (= node @(get-in worker-config [:state :leader-id]))))))))
    (worker/signal-terminate :control worker-config)
    (<!! running-worker)
    (log/infof "Test subject has been shutdown.%s" bar)))
