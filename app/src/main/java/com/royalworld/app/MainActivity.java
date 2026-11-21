private boolean handleUrl(String url) {
                if (url.startsWith("royalworld://")) {
                    handleDeepLinkUrl(url);
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Sirf Key Generation, Ad-Shortlinks aur Social links bahar Chrome mein khulenge
                    if (url.contains("key-generator") ||
                        url.contains("shrinkme") ||
                        url.contains("gplinks") ||
                        url.contains("t.me/") ||
                        url.contains("wa.me/") ||
                        url.contains("whatsapp.com") ||
                        url.contains("instagram.com") ||
                        url.contains("youtube.com") ||
                        url.contains("youtu.be")) {
                        openInChromeBrowser(url);
                        return true;
                    }
                    // Baaki SAB (Live Tests, Courses, Website) app ke andar hi khulega
                    return false;
                }
                return false;
            }