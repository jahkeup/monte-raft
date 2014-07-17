(ns monte-raft.utils
  (:require [clojure.test :refer :all]))

(defmacro after-mseconds
  "Perform body after waiting for msecs"
  [msec & body]
  `(do
     (Thread/sleep ~msec)
     ~@body))

;; http://techbehindtech.com/2010/06/01/marking-tests-as-pending-in-clojure/
;; A Simple macro that enable to mark your test
;; to pending
(defmacro deftest-pending [name & body]
 (let [message (str "\n========\n" name " is pending !!\n========\n")]
   `(deftest ~name
         (println ~message))))
