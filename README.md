![](logo/horizontal.png)

# Welcome to Coast

## What is this?

Coast is a full stack web framework written in Clojure for small teams or solo developers. It uses a relational database and renders html on the server without javascript which allows you to ship your web applications faster.

## Getting Started

Create a new coast website from your terminal

```bash
brew install clojure
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast
chmod a+x /usr/local/bin/coast
coast new myapp
cd myapp
make server
```

You should be greeted with the text "You're coasting on clojure!"
when you visit `http://localhost:1337`

## Read The Docs

The docs are still under construction, but there should be enough there
to get a production-ready website off the ground

[Read the docs](docs/README.md)

## Contributing

Any contribution is welcome! Submit a PR and let's get it merged in!

## License

Coast on Clojure is released under the [MIT License](https://opensource.org/licenses/MIT).
