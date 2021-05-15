# Polyhedra Explorer

**🚧 Work in process.**

Interactive polyhedra explorer with animated transformations. This project is focused on regular convex 
polyhedra and derivation of larger polyhedra via 
[Conway polyhedron notation](https://en.wikipedia.org/wiki/Conway_polyhedron_notation).
All transformations are symmetry-preserving and all resulting elements (faces, edges, vertices) 
are grouped into rotation orbits and are colored by default with respect to them.  

Prototype is deployed at [http://polyhedron.me](http://polyhedron.me)
                                              
## Building & running

```shell
gradlew jsBrowserDevelopmentRun 
```

## Roadmap / TODO
                       
* UI/UX
  * [ ] Animate seed changes with fly in/out
  * [ ] Better progress bar display
  * [ ] Show/kind faces by kind with point and click on the polyhedron
  * [ ] Mark experimental features in UI
  * [ ] Better slider UI on mobile devices
* Export/Share 
  * [x] Solid to STL
  * [x] Geometry to OpenSCAD  
  * [ ] Picture to SVG
  * [ ] Share link
* Polyhedra
  * [ ] Bigger library of seeds
      * [x] Platonic solids
      * [x] Arhimedean solids
      * [x] Catalan solids
      * [ ] Infinite families of prisms/antiprisms 
      * [ ] Johnson solids
  * [ ] Identify names of well-know polyhedra
* Rendering
  * [ ] Render nicer edges and vertices
  * [ ] Render better-looking (physical) materials 
  * [ ] Custom faces coloring: by orbit with reflections, by geometry, by size
  * [ ] Nicer-looking transparent views (only transparent front)
* Polyhedron info
  * [ ] Show edge geometry (two faces)
  * [ ] Show face areas
  * [ ] Sort by selected column (kind/distance/length/area)
* Transformations
  * [ ] Redesign truncation algorithm so that it always works
  * [ ] Rectification solution for non-regular polyhedra  
  * [ ] Stellation
  * [ ] Better canonical algorithm
  * [ ] Long-term caching of canonical geometry keyed by topology
  * [ ] Improve transformation performance
* Custom transformations
  * [ ] Truncate specific vertices
  * [ ] Cantellate specific edges
  * [ ] Augment specific faces
  * [ ] Improve dropping of selected vertices/faces/edges
* Infrastructure    
  * [ ] Embed CSS into WebPack
  * [ ] Drop gl-matrix
  * [ ] Switch from React to Compose
  * [ ] Benchmarking
  * [ ] Software gl impl: render polyhedra picture by params on backend
        
## License

Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
