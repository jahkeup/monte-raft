(ns monte-raft.node.socket-test
  (:require [clojure.test :refer :all]
            [monte-raft.node.socket :as socket]
            [zeromq.zmq :as zmq]
            [monte-raft.utils :refer :all]))

(defonce context (zmq/context))

(deftest test-socket-heartbeat
  (with-open [listener (socket/make-heartbeat-listener context "inproc://heartbeat")
              requestor (socket/make-leader-connector context "inproc://heartbeat")]
    (let [msg "HEARTBEAT" response "PONG"]
                (zmq/send-str requestor msg)
                (is (= (zmq/receive-str listener) msg))
                (zmq/send-str listener response)
                (is (= (zmq/receive-str requestor) response)))))

(deftest test-change-publication
  (let [binding "inproc://updates"]
    (with-open [pub (socket/make-state-update-publisher context binding)
                sub (socket/make-state-update-subscriber context binding)
                sub2 (socket/make-state-update-subscriber context binding)]
      (let [state "blah: here's state!"]
        (doall (for [s [sub sub2]]
                 (zmq/subscribe s "")))
        (zmq/send-str pub state)
        (Thread/sleep 1000)
        (testing "Receives published string"
          (doall (for [s [sub sub2]]
                   (is (= (zmq/receive-str s) state)))))))))

(deftest test-receive-with-timeout
  (let [bind-to "inproc://testing"
        msg "testing testing 1 2 3"]
    (with-open [req (doto (zmq/socket context :req)
                      (zmq/bind bind-to))
                rep (doto (zmq/socket context :rep)
                      (zmq/connect bind-to))]
      (testing "Receive within timeout"
        (future (after-mseconds 0
                  (zmq/send-str req msg)))
        (is (= (socket/receive-str-timeout rep 1000) msg))))))

(deftest test-receive-outside-timeout
  (let [bind-to "inproc://testing"
        msg "testing testing 1 2 3"]
    (with-open [req (doto (zmq/socket context :req)
                              (zmq/bind bind-to))
                rep (doto (zmq/socket context :rep)
                      (zmq/connect bind-to))]
      (testing "Recieve outside timeout should be nil"
        ;; Never sending should force the timeout to return, but may
        ;; cause the socket to become locked up..
        (is (= (socket/receive-str-timeout rep 1) nil))
        (zmq/send-str req msg)
        (let [response (socket/receive-str-timeout rep 1000)]
          (is (not (nil? response))))))))
