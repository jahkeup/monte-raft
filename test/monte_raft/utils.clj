(ns monte-raft.utils)

(defmacro after-mseconds
  "Perform body after waiting for msecs"
  [msec & body]
  `(do
     (Thread/sleep ~msec)
     ~@body))
