# Polyhedra Explorer

**🚧 Work in process.**

Interactive polyhedra explorer with animated transformations. This project is focused on regular convex 
polyhedra and derivation of larger polyhedra via 
[Conway polyhedron notation](https://en.wikipedia.org/wiki/Conway_polyhedron_notation).
All transformations are symmetry-preserving and all resulting elements (faces, edges, vertices) 
are grouped into rotation orbits and are colored by default with respect to them.  

**👷🏽 It is an engineering prototype. There is no nice UI yet.**
                                              
## Building & running

```shell
gradlew jsBrowserDevelopmentRun 
```

## Roadmap / TODO
                       
* Animations
  * [x] Animate all param changes
  * [x] Animate polyhedra transformations
  * [x] Animate topology-preserving transformations (canonicalization)
  * [ ] Animate multi-step transformations
* Asynchrony 
  * [x] Move canonicalization algo to a WebWorker
  * [ ] Better progress bar display
  * [x] Cancellation
* Export 
  * [ ] Solid to STL
  * [x] Geometry to OpenSCAD  
  * [ ] Picture to SVG
* Polyhedra
  * [ ] Bigger library of seeds
      * [x] Platonic solids
      * [x] Arhimedean solids
      * [ ] Catalan solids
      * [ ] Johnson solids
  * [ ] Identify names of well-know polyhedra
* Rendering
  * [ ] Render nicer edges and vertices
  * [ ] Render better-looking (physical) materials 
  * [ ] Render hollow models with select faces
  * [ ] Custom faces coloring: by orbit with reflections, by geometry, by size
  * [ ] Nicer-looking transparent views (only transparent front)
* Interaction
  * [x] Pinch-to-zoom gesture (or Ctrl+Wheel)
  * [x] Z-rotate with Shift+Mouse 
  * [ ] Two-finger Z-rotate gesture (does not seem to be supported on macOS Chrome)
  * [ ] Select/show/hide faces by kind with point and click
* Polyhedron info
  * [ ] Show face and vertex geometry
  * [ ] Show edge dihedral angles
* Transformations
  * [ ] Merge face kind when possible
  * [ ] Redesign truncation algorithm so that it always works
  * [ ] Rectification solution for non-regular polyhedra  
  * [ ] Stellation
  * [x] Chamfering
* Custom transformations
  * [ ] Truncate specific vertices
  * [ ] Cantellate specific edges
  * [ ] Augment specific faces
  * [ ] Drop selected vertices
* Infrastructure    
  * [ ] Embed CSS into WebPack
  * [ ] `jsBrowserProductionRun` shall work
* Overall style / layout
  * [ ] Move inessential params to popup
  * [ ] Flip left-right through seeds and transforms
  * [ ] Design and implement a full-screen UI
* Backend
  * [ ] Render polyhedra picture by params
        
## License

Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
