# Third-party notices

SableGuard includes code adapted from the following MIT-licensed projects. Each file that contains
adapted code says so in its header or class comment. The MIT license requires this notice to be kept.

## SableCat — https://github.com/eviesaurr/SableCat

Copyright (c) 2025 FuckSable Contributors
Copyright (c) 2026 SableCat Contributors

## FuckSable / CACB — https://github.com/XSY-HYH/fuck-sable

Copyright (c) 2025 FuckSable Contributors

## Sable Fix Mod — https://github.com/finkkk/sable-fix-mod

Copyright (c) 2026 finkkk飞飞

## License text (identical for the three projects)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## Ideas reimplemented without code

- **Sable Chunk Guard** (All Rights Reserved): no code was copied or decompiled. SableGuard's
  `chunk-guard` fix is an independent implementation of the same idea (physics must not load chunks
  synchronously), written against Sable's public source.
- **Sable Holding Chunk Fixer** (All Rights Reserved): no code was copied or decompiled. Only its public
  description and the names of its classes and methods were used to understand what it changes. The
  `keep-sublevels-near-players` and `death-grace` fixes are independent implementations written against Sable's
  public source.
- **CBC Sable Rangefinder Fix** (MIT, `cbc_sable_fix`, by Yarik): read to understand the fix; SableGuard's
  `cbc-rangefinder-sublevel-fix` is written independently (different injection point and result) and contains no
  code from it.
- **CBC Ballistics** (MIT, https://www.curseforge.com/minecraft/mc-mods/cbc-ballistics, by Felis): SableGuard contains
  no CBC Ballistics code; its mixins only target CBC Ballistics methods, located by reading the 1.3.0a jar.
