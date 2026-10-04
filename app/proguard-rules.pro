# App-specific R8 keep rules for the release build.
#
# Media3, Compose, Lifecycle and kotlinx.coroutines ship their own consumer rules, and the app uses no
# reflection of its own, so no extra rules are needed. Add a keep rule here only if the release smoke
# test (launch + a short session) proves that R8 removed or renamed something the app needs.
