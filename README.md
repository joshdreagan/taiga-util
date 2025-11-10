# taiga-util

This utility can be used to convert cards from a Taiga export JSON file into Google Docs.

## Prerequisites

Create a Google project, enable API access, and create a OAuth2 client. You can use the following "Getting Started" guides as a reference:

- [Google Drive - Getting Started](https://developers.google.com/workspace/drive/api/quickstart/java)
- [Google Docs - Getting Started](https://developers.google.com/workspace/docs/api/quickstart/java)

Use "taiga-util" as the application name.


## Building

```
mvn clean package
```

## Running

There are 2 commands: 'split' and 'googlify'. Run the help command for more info.

```
java -jar target/taiga-util-1.0-SNAPSHOT-runner.jar --help
```

You'll want to start by running 'split' on the raw Taiga export JSON file. This will generate a file for each user story (aka card).

Example:

```
java -jar target/taiga-util-1.0-SNAPSHOT-runner.jar split --output-directory ./output ./taiga-raw-export.json
```

You can check the difference between two exports by running `diff -qr oldDir newDir`. This will give you a list of files that have changed. That way you don't have to run the 'gooflify' command and reprocess every card. You can just reprocess the new or changed cards.

Next, you'll want to run 'gooflify' on each card.

Example:

```
java -jar target/taiga-util-1.0-SNAPSHOT-runner.jar gooflify --credentials ./credentials.json --oauth-user "oauth-user" --folder-id "1oyQgF5i_Avi1pfZntAJNgAx-iaskQfSj" ./output/1.json
```

You can pass multiple files to 'gooflify' at once. You can do so either manually, or using something like `xargs`.

Example:

```
find ./output -type f -iname '10???' -print0 | xargs -0 java -jar target/taiga-util-1.0-SNAPSHOT-runner.jar gooflify --credentials ./credentials.json --oauth-user "oauth-user" --folder-id "1oyQgF5i_Avi1pfZntAJNgAx-iaskQfSj"
```

Each doc will take a few seconds to process/create. So it's best to run small batches of files at a time.