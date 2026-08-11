# Keyboard navigation

Keyboard shortcuts mirror the actions and ordering of the controls currently visible on screen. They operate on the last transform in the chain, or on the seed when the chain is empty. Popup shortcuts are toggles: pressing the same key again closes that popup, and opening another popup replaces the current one.

## Shortcuts

| Key | Action |
| --- | --- |
| Left / Right | Select the previous or next last transform; with no transforms, select the previous or next seed. |
| Up / Down | Move through the Add, F/E/V, or saved-configuration popup rows. Otherwise cycle the selected target orbit, or increase/decrease the size of a family seed according to its displayed buttons. |
| Enter | Activate the highlighted Add or saved-configuration row. On the main screen, accept the visible algebraic replacement, or the catalog-seed replacement when no algebraic replacement is shown. |
| `+` | Toggle the Add transform popup. |
| Delete / Backspace | Delete the last transform. In seed-only state, reset the seed and remembered family size. Backspace provides the same convenient action on macOS. |
| Escape | Close the currently open popup. |
| Space | In the Faces popup, show or hide the highlighted face orbit. Elsewhere, hide all face orbits when all are visible, or show all face orbits. |
| `F` / `E` / `V` | Toggle the faces, edges, or vertices inspection popup. |
| `Y` | Show or hide symmetry planes and rotation axes. |
| `R` | Start or stop automatic rotation. |
| `C` | Toggle the configuration popup. |
| `X` | Toggle the export popup; it also closes the print-color picker. |
| `S` | Toggle the saved-configurations popup. |
| `?` | Toggle keyboard help. |

Arrow navigation uses the same option lists, bounds, orbit memory, wrapping rules, and update paths as the on-screen controls. Add-menu, F/E/V, and saved-configuration row selection wrap at either end, remain highlighted, and scroll into view. “Save current” is the first saved-configuration row: Enter saves there, while Enter on a saved entry loads it. Enter does nothing when there is no applicable highlighted item or replacement, and it does not pass through an unrelated open popup.

## Focus and discoverability

Global shortcuts are suspended when an input, slider, dropdown, text area, or editable element has focus, preserving native typing and control navigation. Escape remains active there so every popup can always be dismissed from the keyboard. Unmodified letter keys are case-insensitive; Ctrl, Alt, and Command/Meta combinations are left to the browser and operating system. Shift remains available for producing `+` and `?`.

The `?` button below Save opens the same help table in the application. Its version label is generated from the Gradle project version for local builds and from the semantic-version release tag in GitHub Actions.
