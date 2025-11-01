(def factorial (lambda (n acc)
    (if (= n 0)
        acc
        (factorial (- n 1) (* n acc)))))

(print (factorial 10 1))
(print (factorial 20 1))
(print (factorial 1000 1))
