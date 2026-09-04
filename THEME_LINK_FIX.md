# v1.3.3 theme link fix

The Home screen no longer assumes a single case-sensitive Aiki Theme URL.

At refresh time it tries the canonical Hostinger variants and remembers the first URL that returns a real page. The same resolved URL is then used when the user taps the weekly Aiki Theme card.

Candidates:
- https://www.shinyuembody.org/Aiki-theme
- https://www.shinyuembody.org/aiki-theme
- https://shinyuembody.org/Aiki-theme
- https://shinyuembody.org/aiki-theme

This avoids a 404 caused by hostname or path-case differences.
