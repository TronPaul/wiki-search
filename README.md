# wiki-search

*Confluence search for humans*

Wiki-search provides a Confluence content indexer and a command line search tool. It's goal
is to provide more relevant results than Confluence's built-in search. The search tool is
a native image built by GraalVM which should provide the responsiveness needed for a command
line search tool.

## Usage

### Search

    clojure -m search -s [space-key] --elasticsearch-url [elasticsearch-url] [search text...]...

### Indexing

    clojure -m index -s [space-key] --elasticsearch-url [elasticsearch-url] --force-index-creation confluence-base-url

## Development

Wiki-search's search tool is designed to be able to be built into a native image by
GraalVM. This makes building and distributing a bit tricky.

### Prerequisites

1. [Clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools)
2. [GraalVM](https://www.graalvm.org/downloads/)

    a. [native-image component](https://www.graalvm.org/docs/reference-manual/native-image/)

### Building

#### wiki-search native index

    clojure -R:bg -A:native-image
