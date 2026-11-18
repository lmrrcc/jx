# JX artwork

[jx-mark.png](jx-mark.png) is the compact project icon used in the README hero,
with the project name and introduction rendered as selectable text below it.
The ivory tile keeps the forest-green and terracotta monogram readable on both
light and dark page backgrounds. Its surrounding background is transparent.

[jx-banner.png](jx-banner.png) is the separate 1731 × 909 pixel **LinkedIn post
image**. Its complete headline, code sample, and preview are designed for social
sharing.

Both assets use the palette from the
[Todo application stylesheet](../../examples/todo-app/src/main/web/styles.css):

| Role | Color |
| --- | --- |
| Warm ivory background | `#f5f4ef` |
| Forest text | `#24352d` |
| Primary green | `#2e5543` |
| Sage surface | `#e8ece3` |
| Terracotta accent | `#bd713d` |
| Muted text | `#6d7a6f` |
| Dividers | `#d9dfd5` |

The serif monogram and headline, quiet surfaces, and light code panel follow the
Todo app's visual direction. The counter uses the current `@Component` annotation.
Its markup uses `onClick={increment(count)}` with the extracted `EventHandler`
helper shown below `render()`, matching the README's event-handler pattern.

Created with the built-in image generation tool. The [mark prompt](mark-prompt.txt)
records the icon design. The [banner prompt](banner-prompt.txt) records the social
image's text, layout, and palette, using the user's previous cover as its
composition reference. Code is promotional artwork; the README's selectable code
block and repository examples are the source to copy and compile.
