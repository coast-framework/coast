(ns coast.db.sql
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [coast.utils :as utils]
            [coast.time2 :as time2])
  (:refer-clojure :exclude [update]))


(def ops #{:select :from :update :set :insert
           :delete :pull :join :left-join :right-join
           :left-outer-join :right-outer-join :outer-join
           :full-outer-join :full-join :cross-join
           :where :order :limit :offset :group :values})


(defn op? [k]
  (contains? ops k))


(defn ? [val]
  (cond
    (and (coll? val)
         (every? coll? val)) (->> (map ? val)
                                  (string/join ", "))
    (coll? val) (->> (map ? val)
                     (string/join ", ")
                     (utils/surround "()"))
    (nil? val) "null"
    :else "?"))


(defn not-op [op* val]
  (cond
    (sequential? val) "not"
    (nil? val) "is not"
    :else (name op*)))


(defn op [op* val]
  (cond
    (sequential? val) "in"
    (nil? val) "is"
    :else (name op*)))


(defn select-col [k]
  (if (qualified-ident? k)
    (str (utils/sqlize k)
         " as "
         (-> k namespace utils/snake-case) "$" (-> k name utils/snake-case))
    (utils/sqlize k)))


(defn select [v]
  (let [distinct (first (filter #(= (name %) "distinct") v))
        distinct (if (ident? distinct)
                   (str "distinct ")
                   "")
        columns (filter #(not= (name %) "distinct") v)
        s (->> (map select-col columns)
               (string/join ", "))]
    (if (not (string/blank? s))
      {:select (str "select " distinct s)
       :select-ks v}
      (throw (Exception. (str "select needs at least one argument."))))))


(defn where-part [v]
  (if (not (sequential? v))
    (throw (Exception. (str "where requires vectors to work. You typed: " v)))
    (if (utils/sql-vec? v)
      (first v)
      (let [k (first v)
            [op* val] (if (> 2 (count v))
                        [(second v) (nth v 2)]
                        ['= (second v)])
            parts (if (= '!= op*)
                    [(utils/sqlize k) (not-op op* val) (? val)]
                    [(utils/sqlize k) (op op* val) (? val)])]
        (string/join " " parts)))))


(defn last-or-rest [v]
  (if (utils/sql-vec? v)
    (rest v)
    (last v)))


(defn where [v]
  (if (utils/sql-vec? v)
    {:where (str "where " (first v))
     :args (rest v)}
    (let [v (if (> (utils/depth v) 2)
              (first v)
              v)]
      {:where (str "where " (string/join " and " (mapv #(utils/surround "()" %) (mapv where-part v))))
       :args (->> (filter vector? v)
                  (mapv last-or-rest)
                  (filter some?)
                  (utils/flat))})))


(defn insert [v]
  (let [table (-> v first namespace utils/snake-case)]
    {:insert (str "insert into " table " ("
                  (->> (map name v)
                       (map utils/snake-case)
                       (string/join ", "))
                 ")")}))


(defn values [v]
  {:values (str "values " (? v))
   :args v})


(defn update [v]
  (let [table (-> v first utils/snake-case)]
    {:update (str "update " table)}))


(defn update-set [v]
  (let [v (conj v [:updated-at (time2/now)])
        args (filter #(not= "id" (-> % first name)) v)]
    {:update-set (str "set " (->> (map (fn [[k _]] (str (-> k name utils/snake-case) " = ?")) args)
                                  (distinct)
                                  (string/join ", ")))
     :update-set-args (map second args)}))


(defn from [v]
  {:from (str "from " (string/join " " (map utils/sqlize v)))})


(defn delete [v]
  (merge (from v) {:delete (str "delete")}))


(defn limit [[i]]
  (if (pos-int? i)
    {:limit (str "limit " i)}
    (throw (Exception. (str "limit needs a positive integer. You typed: :limit " i)))))


(defn offset [[i]]
  (if (not (neg-int? i))
    {:offset (str "offset " i)}
    (throw (Exception. (str "offset needs a positive integer. You typed: :offset " i)))))


(defn group [v]
  {:group (str "group by " (->> (map utils/sqlize v)
                                (string/join ", ")))})


(defn order [v]
  {:order (str "order by " (->> (partition-all 2 v)
                                (mapv vec)
                                (mapv #(if (= 1 (count %))
                                         (conj % 'asc)
                                         %))
                                (mapv #(str (utils/sqlize (first %)) " " (name (second %))))
                                (string/join ", ")))})


(defn join [s m]
  (if (map? m)
    (str s " " (name (:join/table m))
         " on " (utils/sqlize (:join/left m))
         " = " (utils/sqlize (:join/right m)))
    (str s " " m)))


(defn join-map [from val]
  (cond
    (ident? val) {:join/table (utils/sqlize val)
                  :join/left (utils/sqlize (keyword (name val) from))
                  :join/right (utils/sqlize (keyword from "id"))}
    (vector? val) {:join/table (first val)
                   :join/left (second val)
                   :join/right (nth val 2)}
    :else val))


(def join-sql-map {:join "join"
                   :left-join "left join"
                   :right-join "right join"
                   :left-outer-join "left outer join"
                   :right-outer-join "right outer join"
                   :outer-join "outer join"
                   :full-outer-join "full outer join"
                   :full-join "full join"
                   :cross-join "cross join"})


(defn joins [k m]
  (let [from (utils/sqlize (first (:from m)))
        join-str (get join-sql-map k)]
    (assoc m k (->> (map #(join-map from %) (get m k))
                    (map #(join join-str %))
                    (string/join " ")))))


(defn all-joins [m]
  (->> (joins :join m)
       (joins :left-outer-join)
       (joins :left-join)
       (joins :right-join)
       (joins :right-outer-join)
       (joins :outer-join)
       (joins :full-join)
       (joins :cross-join)))


(defn no-asterisks? [m]
  (empty? (filter #(= (name %) "*") (:select m))))


(defn ns-or-name [k]
  (when (ident? k)
    (or (namespace k) (name k))))


(defn select-ns-keys [key-ns m]
  (->> (filter (fn [[k _]] (qualified-ident? k)) m)
       (filter (fn [[k _]] (= (name key-ns) (namespace k))))
       (into {})))


(defn sql-map [v]
  (let [parts (partition-by op? v)
        ops (take-nth 2 parts)
        args (filter #(not (contains? (set ops) %)) parts)]
    (zipmap (map first ops) (map vec args))))


(defn rel-opts [m]
  (let [k (-> m keys first)]
    (if (sequential? k)
      (sql-map (drop 1 k))
      {})))


(defn pull-limit [[i]]
  (when (pos-int? i)
    {:limit (str "where rn <= " i)}))


(defn pull-sql-part [[k v]]
  (condp = k
    :order (order v)
    :limit (pull-limit v)
    :else {}))


(defn join-statement [{:keys [table left right]}]
  (str (utils/sqlize table)
       " on "
       (utils/sqlize left)
       " = "
       (utils/sqlize right)))


(defn pull-from [pf-joins o table]
  (let [[first-join second-join] pf-joins
        order-by-id (or (:left second-join) "id")
        order (or o (str "order by " order-by-id))
        select (->> (map :table pf-joins)
                    (map #(str % ".*,"))
                    (string/join "\n"))]
    (string/join "\n" ["("
                       "select " select
                       (str "    row_number() over (" order ") as rn")
                       (str "from " table)
                       (if (some? second-join)
                         (str " join " (join-statement (merge second-join {:table (:table first-join)})))
                         "")
                       (str ") as " table)])))


(defn rel-key [m]
  (let [k (-> m keys first)]
    (if (sequential? k)
      (first k)
      k)))


(defn pull-col [k]
  (str (-> k namespace utils/sqlize) "$" (-> k name utils/sqlize)))


(defn json-build-object [k]
  (str "'" (pull-col k) "', " (utils/sqlize k)))


(defn pull-op? [k]
  (contains? #{:limit :order} k))


(defn pull-sql-ops [v]
  (let [parts (partition-by pull-op? v)
        ops (take-nth 2 parts)
        args (filter #(not (contains? (set ops) %)) parts)]
    (zipmap (map first ops) (map vec args))))


(defn rel-col [k]
  (if (qualified-ident? k)
    (str "'" (pull-col k) "', " (pull-col k))
    (str "'" (-> k first pull-col) "', " (-> k first pull-col))))


(def pull-sql-map {"sqlite" {:json-agg "json_group_array"
                             :json-object "json_object"}
                   "postgres" {:json-agg "json_agg"
                               :json-object "json_build_object"}})


(defn pull-join [adapter associations m]
  (let [k (rel-key m)
        association (get associations k)
        {:keys [from col]} association
        pull-join-map (get-in association [:joins 0])
        pf-joins (get association :joins)
        {:keys [order limit]} (->> (rel-opts m)
                                   (map pull-sql-part)
                                   (apply merge))
        val (-> m vals first)
        v (filter qualified-ident? val)
        maps (filter map? val)
        child-cols (mapv #(-> % keys first rel-col) maps)]
    (->> ["left outer join ("
          "select"
          (str col
               ",")
          (str (get-in pull-sql-map [adapter :json-agg])
               "("
              (get-in pull-sql-map [adapter :json-object])
              "(")
          (->> (map json-build-object v)
               (concat child-cols)
               (string/join ","))
          (str ")) as " (pull-col k))
          (str "from " (pull-from pf-joins order from))
          (->> (map #(pull-join adapter associations %) maps)
               (string/join "\n"))
          limit
          (str "group by " col)
          (str ") " (join-statement pull-join-map))]
         (filter some?)
         (string/join "\n"))))


(defn pull-joins [adapter associations acc v]
  (let [maps (filter map? v)
        joins (mapv #(pull-join adapter associations %) maps)
        acc (concat acc joins)]
    (if (empty? maps)
     acc
     (pull-joins adapter associations acc (map #(-> % vals first) maps)))))


(defn pull
  "Converts the :pull key of a map into a sql map"
  [adapter associations m]
  (if (not (contains? m :pull))
    m
    (let [v (:pull m)
          cols (filter qualified-ident? v)
          maps (filter map? v)
          rel-cols (->> (mapv rel-key maps)
                        (mapv pull-col))
          col-sql (string/join ", " (concat (mapv select-col cols)
                                            rel-cols))
          joins (pull-joins adapter associations [] v)]
      (assoc m :select (str "select " col-sql)
               :join (string/join "\n" joins)))))


(defn pull-vec [k p-vec cm am]
  (if (empty? cm)
    p-vec
    (let [next-p-vec (if (empty? p-vec)
                        (get cm k)
                        p-vec)
          has-many-ks (->> (keys am)
                           (filter #(= (namespace %) (name k))))
          has-many-cols (->> (mapv #(hash-map % (get cm (:has-many (get am %)))) has-many-ks)
                             (filter #(some? (first (vals %)))))
          next-p-vec (concat next-p-vec has-many-cols)
          cm (dissoc cm k)]
      (pull-vec (first (keys cm))
        next-p-vec
        cm
        (apply dissoc am has-many-ks)))))


(defn expand-pull-asterisk [associations col-map m]
  (if (and (contains? m :pull)
           (= "*" (name (first (:pull m))))
           (not (empty? associations)))
    (let [table (keyword (first (:from m)))
          pull (pull-vec table [] col-map associations)]
      (assoc m :pull pull))
    m))


(defn expand-select-asterisks [col-map m]
  (if (or (no-asterisks? m)
          (not (contains? m :select)))
    m
    (let [from-tables (map ns-or-name (:from m))
          joins-tables (mapcat #(ns-or-name (get m %)) (keys join-sql-map))
          select-tables (map namespace (filter qualified-ident? (:select m)))
          tables (->> (concat from-tables joins-tables select-tables)
                      (filter some?)
                      (map name)
                      (map keyword))
          columns (mapcat identity
                          (map #(get col-map %) tables))]
      (assoc m :select (->> (concat (:select m) columns)
                            (filter #(not= (name %) "*"))
                            (vec))))))


(defn qualify-col [table k]
  (if (= (name k) "distinct")
    :distinct
    (if (qualified-ident? k)
      k
      (keyword (name table) (name k)))))


(defn expand-select [m]
  (if (contains? m :select)
    (let [columns (:select m)
          from (-> m :from first)]
      (assoc m :select (mapv #(qualify-col from %) columns)))
    m))


(defn replace-val [q params val]
  (if (and (ident? val)
           (string/starts-with? (str val) "?"))
    (let [rk (-> (string/replace-first val #"\?" "")
                 (keyword))
          rv (get params rk)]
      (if (not (contains? params rk))
        (throw (Exception. (str "Parameter " val " is missing in query " q)))
        rv))
    val))


(defn replace-vals [v params]
  (walk/prewalk (partial replace-val v params) v))


(defn sql-part [adapter [k v]]
  (condp = k
    :select (select v)
    :from (from v)
    :pull {:pull v}
    :join {:join v}
    :cross-join {:cross-join v}
    :left-join {:left-join v}
    :right-join {:right-join v}
    :outer-join {:outer-join v}
    :full-join {:full-join v}
    :left-outer-join {:left-outer-join v}
    :right-outer-join {:right-outer-join v}
    :where (where v)
    :order (order v)
    :limit (limit v)
    :offset (offset v)
    :group (group v)
    :group-by (group v)
    :delete (delete v)
    :values (values v)
    :insert (insert v)
    :update (update v)
    :set (update-set v)
    nil))

(defn fmt-pull [m]
  (if (contains? m :pull)
    (if (and (vector? (:pull m))
             (vector? (first (:pull m))))
      (assoc m :pull (first (:pull m)))
      m)
    m))

(defn sql-vec
  "Generates a jdbc sql vector from an ident sql vector"
  [adapter col-map associations v params]
  (let [m (->> (replace-vals v params)
               (sql-map)
               (expand-select-asterisks col-map)
               (fmt-pull)
               (expand-pull-asterisk associations col-map)
               (expand-select)
               (all-joins)
               (map #(sql-part adapter %))
               (apply merge)
               (pull adapter associations))
        {:keys [select join left-join right-join
                left-outer-join right-outer-join full-join
                full-join cross-join full-outer-join
                where order offset
                limit group args delete
                insert values update from
                update-set update-set-args]} m
        sql (->> (filter #(not (string/blank? %)) [select delete from
                                                   update update-set insert values
                                                   join left-join right-join
                                                   left-outer-join right-outer-join
                                                   full-join cross-join full-outer-join
                                                   where order offset limit group])
                 (string/join " "))]
    (apply conj [sql] (concat update-set-args (filter some? (utils/flat args))))))
