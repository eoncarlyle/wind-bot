(ns wind-bot.core
  (:gen-class)
  (:require
    [environ.core :refer [env]]
    [overtone.at-at :as overtone]
    [twitter.api.restful :as twitter]
    [twitter.oauth :as twitter-oauth]
    [clojure.math.numeric-tower :as math]
    [clojure.string :as string]))

(require '[clojure.java.io :as io])
(require '[clojure.edn :as edn])
(require '[clj-time.core])
(def my-pool (overtone/mk-pool))

(def my-creds
  (twitter-oauth/make-oauth-creds (env :app-consumer-key)
                                  (env :app-consumer-secret)
                                  (env :user-access-token)
                                  (env :user-access-secret)))
(defn exp
  [x n]
  (reduce * (repeat n x)))

(defn average
  [numbers]
  (/ (apply + numbers) (count numbers)))

(defn vestasPowerCurve
  "Input: v, wind velocity in units m/s. Returns: power in units kW."
  [v]
  (
    let [lead-vec [1588.39  0 -1884.05 1501.41 -557.88 119.619
      -15.5994 1.22303 -0.0530215 0.000976165]
      trail-vec [-15959.1 0 765.772 -83.7096 2.57538] 
      peak 3073]
      (cond
        (and (<= v 3)) 0
        (and (> v 3) (<= v 11)) (reduce + (map * lead-vec (map #(exp v %1) (range))))
        (and (> v 11) (<= v 12.5)) (reduce + (map * trail-vec (map #(exp v %1) (range))))
        (> v 12.5) peak)))

;;> String Handling
(defn parse-int
  [s]
  (Integer. (re-find  #"\d+" s)))

(defn lines
  [n filename]
  (with-open [rdr (io/reader filename)]
    (doall (take n (line-seq rdr)))))

(defn linesback
  "Returns the number of lines back neccesary for n elapsed hours of windspeed data"
  ([n hyperlink]
   (let [find-vec (re-find #"(^(\d{4})\s(\d{2})\s(\d{2})\s(\d{2})\s(\d{2}))" (last (lines 3 hyperlink)))
    arg-vec (map parse-int (rest (rest find-vec)))
    search-date (clj-time.core/minus (eval (concat '(clj-time.core/date-time) arg-vec)) (clj-time.core/hours n))]
   (linesback n hyperlink search-date 4)))

  ([n hyperlink search-date line]
   (let
   [find-vec (re-find #"(^(\d{4})\s(\d{2})\s(\d{2})\s(\d{2})\s(\d{2}))" (last (lines line hyperlink)))
    arg-vec (map parse-int (rest (rest find-vec)))
    cur-date (eval (concat '(clj-time.core/date-time) arg-vec)) ]
   (if (= cur-date search-date) line (linesback n hyperlink search-date (inc line)))))) 

(defn txtPower
  "Returns the average power in the last n lines from a given hyperlinke"
  [PowerCurve n hyperlink]
  (Math/round (Math/floor (average (map #(PowerCurve (edn/read-string (last (re-find #"\d+\s+\d+\s+\d+\s+\d+\s+\d+\s+\d+\s+(\d+.\d+)"
                                                                                     (last (lines (+ % 3) hyperlink)))))) (take n (range)))))))

(defn nthLineSpeed
  "Returns the wind speed on the nth line"
  [n hyperlink]
  (edn/read-string (last (re-find #"\d+\s+\d+\s+\d+\s+\d+\s+\d+\s+\d+\s+(\d+.\d+)"
                                  (last (lines (+ n 3) hyperlink))))))

(def tweet-list
  '({:bool-continuous true  :power 0.75   :prephrase "to power "  :postphrase " refrigerators."}
    {:bool-continuous true  :power 0.523  :prephrase "to power "  :postphrase " home water heaters."}
    {:bool-continuous true  :power 0.03   :prephrase "to power "  :postphrase " home AC units."}
    {:bool-continuous false :power 3.64   :prephrase "for "       :postphrase " miles of electric car travel."}
    {:bool-continuous false :power 0.36   :prephrase "for "       :postphrase " years of electric toothbrush use."}
    {:bool-continuous false :power 1.45   :prephrase "to watch every episode of Seinfeld "  :postphrase " times."}
    {:bool-continuous true  :power 0.05   :prephrase "to power "  :postphrase " Roombas."}))

(defn tweet-maker
  [buoy-map key kwPower]
  (def num-turbines (nth '(6 10 30 50 100) (rand 5)))
  (def prodPower (* kwPower num-turbines))
  (def first-sentence
    (clojure.string/join ["A hypothetical wind farm of " (str num-turbines) " Vestas V117 turbines located at NBDC Weather buoy "
                          (name key) " (near " ((buoy-map key) :loc-str) ") would have produced " (str kwPower) " kW of electrical power over the last 12 hours. "]))

  (def second-sentence-first-phrase "This is enough electricty ")
  (def local-tweet-dict (nth tweet-list (rand (count tweet-list))))
  (if (= (local-tweet-dict :bool-continuous))
    (def num (int (math/floor (/ prodPower (local-tweet-dict :power)))))
    (def num (int (math/floor (/ (* 12 prodPower) (local-tweet-dict :power))))))

  (string/join [first-sentence second-sentence-first-phrase (local-tweet-dict :prephrase) (str num) (local-tweet-dict :postphrase)] ))

(defn status-update
  [tweet]
  (println "generated tweet is :" tweet)
  (println "char count is:" (count tweet))
  (when (not-empty tweet)
    (try (twitter/statuses-update :oauth-creds my-creds
                                  :params {:status tweet})
         (catch Exception e (println "Oh no! " (.getMessage e))))))

(def buoy-map
  {:42035 {:link "https://www.ndbc.noaa.gov/data/realtime2/42035.txt" :depth 16 :loc-str "Galveston, TX" :loc-dist "25 miles"}
   :44007 {:link "https://www.ndbc.noaa.gov/data/realtime2/44007.txt" :depth 27 :loc-str "Portland, ME" :loc-dist "13 miles"}
   :41008 {:link "https://www.ndbc.noaa.gov/data/realtime2/41008.txt" :depth 16 :loc-str "Savannah, GA" :loc-dist "46 miles"}
   :42012 {:link "https://www.ndbc.noaa.gov/data/realtime2/42012.txt" :depth 26 :loc-str "Mobile, AL" :loc-dist "51 miles"}
   :46027 {:link "https://www.ndbc.noaa.gov/data/realtime2/46027.txt" :depth 47 :loc-str "Crescent City, CA" :loc-dist "9 miles"}
   :44020 {:link "https://www.ndbc.noaa.gov/data/realtime2/44020.txt" :depth 14 :loc-str "Nantucket, MA" :loc-dist "17 miles"}})

(defn -main
  [& args]
  (overtone/every (* 1000 60 60 8) #(println (let [key (nth (keys buoy-map)
    (rand-int (count (keys buoy-map)))) link ((buoy-map key) :link)]
    (status-update (tweet-maker buoy-map key
    (txtPower vestasPowerCurve (linesback 12 link) link))))) my-pool))

