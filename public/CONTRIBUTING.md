# ari-proxy Developer Guidelines
These guidelines are meant to be a living document that should be changed and adapted as needed. We encourage changes that make it easier to achieve our goals in an efficient way.

## Steps for Contributing
This is the process for committing code to the ari-proxy project. There are of course exceptions to these rules, for example minor changes to comments and documentation, fixing a broken build etc.

1. Make sure you have signed the [retel.io CLA](https://github.com/retel-io/cla).
2. Before starting to work on a feature or a fix, it's good practice to ensure that:
    1. There is a ticket for your work in the project's [issue tracker](https://github.com/retel-io/ari-proxy/issues);
    2. The ticket has been discussed and prioritized by the team.
3. Fork the project and perform your work in your own git branch (that is, use the [GitHub Flow](https://guides.github.com/introduction/flow/)). Please make sure you adhere to the [Semantic Commit Messages](https://seesparkbox.com/foundry/semantic_commit_messages) format.
4. When the feature or fix is completed you should open a [Pull Request](https://help.github.com/articles/using-pull-requests) on GitHub.
5. The Pull Request will then be reviewed.
6. After the review, you should resolve issues brought up by the reviewers as needed, iterating until the reviewers give their thumbs up.
7. Once the code has passed review the Pull Request can be merged.

## Code style

We use [spotless](https://github.com/diffplug/spotless) to gradually enforce [Google Java Style](https://google.github.io/styleguide/javaguide.html). Your build will fail, if changed files do not comply with the formatting guidelines. To automatically format your changed files correctly, run ` mvn spotless:apply`. 