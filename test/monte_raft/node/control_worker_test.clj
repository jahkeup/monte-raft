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
                        [:term 0]]]
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
          override-handlers {:ping (fn [_] (reset! called? true))}]
      (with-redefs [handlers/cmd-handlers override-handlers]
        (control/handle-message nil "ping")
        (is called?)))))

(deftest-worker test-control-worker-starts
  (let [running-worker (worker/start
                         (control/control-worker (node-config)))]
    (wait-do 100
      (worker/signal-terminate :control (node-config))
      (is (= (<!! running-worker) :terminated)))))

(deftest-worker test-control-reactions
  (let [{:keys [state node-id control-binding kill-codes] :as worker-config} (node-config)
        {:keys [transient current term]} state
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
          "Control should respond with a confirm if transient state is present"
          (reset! transient "some-state")
          (if-let [response (send-recv-timeout sock "CONFIRM")]
            (is (= response "CONFIRMED"))))
        (testing-clean-state state
          "Control should set current upon a COMMIT and respond"
          (let [response (send-recv-timeout sock "COMMIT")]
            (is (= response "COMMITTED"))))))
    (worker/signal-terminate :control worker-config)
    (<!! running-worker)
    (log/infof "Test subject has been shutdown.%s" bar)))

