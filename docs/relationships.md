# Relationships

* [Has Many](#user-content-has-many)
* [Belongs To](#user-content-belongs-to)
* [Has Many Through](#user-content-has-many-through)

In Coast relationships allow you to do more sophisticated queries than you would be able to otherwise.

If you never find yourself needing [pull queries](/docs/pull.md) you can skip this section altogether.

## Basic Example

Here's an example with two database tables: `customer` and `purchase`.

To add the relationship, head over to `db/associations.clj`:

```clojure
(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :customer
      (has-many :purchases))

    (table :purchase
      (belongs-to :customer))))
```

This defines a relationship between `:customer` and `:purchase` where a `customer` has many `purchases` and the purchase table has a `customer` column that references the `customer.id` column.

NOTE: `has-many` attempts to make the keyword you specify singular to match the underlying singular database table name.

If you have a different table name or a table name that can't be turned into a singular, you can specify the table name:

```clojure
(table :customer
  (has-many :stuff-bought :table-name "purchase"))
```

You can also specify the foreign key column as well:

```clojure
(table :customer)
  (has-many :purchases :foreign-key "id"))
```

## Belongs To

Belongs to represents tables with columns referencing another table.

Given these two tables:

- member
  - id
  - screen_name
  - updated_at
  - created_at

- micropost
  - id
  - member references member(id)
  - updated_at
  - created_at

```clojure
(table :micropost
  (belongs-to :member))
```

You can specify a table name that won't be singularized as well:

```clojure
(table :micropost
  (belongs-to :person :table-name "member"))
```

You can also specify the foreign key as well

```clojure
(table :micropost
  (belongs-to :member :foreign-key "id"))
```

## Has Many Through

You can create a "many to many" relationship with `has-many :through`.

Here's an example, with four tables

- author
  - id
  - screen_name
  - updated_at
  - created_at

- post
  - id
  - author
  - title
  - body
  - updated_at
  - created_at

- tag
  - id
  - name unique
  - updated_at
  - created_at

- tagged
  - id
  - post
  - tag
  - updated_at
  - created_at

```clojure
; db/associations.clj
(ns associations
  (:require [coast.db.associations :refer [table belongs-to has-many tables]]))

(defn associations []
  (tables
    (table :author
      (has-many :posts))

    (table :post
      (belongs-to :author)
      (has-many :tagged)
      (has-many :tags :through :tagged))

    (table :tag
      (has-many :tagged)
      (has-many :posts :through :tagged))

    (table :tagged
      (belongs-to :post)
      (belongs-to :tag))))
```

Has many through allows you to shortcut through database tables and pull data out in this way:

```clojure
(coast/pull '[author/screen-name
              {:author/posts [post/title post/body
                              {:post/tags [tag/name]}]}]
            {:author/id 1})
```

For more information on pull syntax and how it works and relates to `db/associations.clj` check out the [pull syntax doc](/docs/pull.md)
