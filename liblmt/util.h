
const char *strskip (const char *s, int n, char sep);
char *strskipcpy (const char **sp, int n, char sep);
char *strappendfield (char **s1p, const char *s2, char sep);

char *xstrdup (const char *s);
char *xstrndup (const char *s, size_t n);
void *xmalloc (size_t size);
void *xrealloc (void *ptr, size_t size);

List list_tok (const char *s, char *sep);

/*
 * vi:tabstop=4 shiftwidth=4 expandtab
 */
