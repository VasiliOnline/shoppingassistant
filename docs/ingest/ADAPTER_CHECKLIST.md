# Adapter Readiness Checklist

Use this checklist when adding a new source adapter.

- URL patterns registered (hosts/regex), resolver mapping verified
- URL normalization rules confirmed (client + server)
- canonicalUrl and listingId extraction validated
- ingest fields mapped (title, price, currency, images, attributes)
- price-only probe implemented on server
- source registry flags set (rolloutEnabled, canIngest, canTrackPrice)
- ingest logging + metrics wired (status, latency, http status, parserVersion)
- graceful error mapping (UNSUPPORTED, TEMP_BLOCKED, NETWORK_ERROR, PARSE_ERROR)
- parserVersion bumped for breaking parser changes
- replay harness enabled for sample HTML capture
