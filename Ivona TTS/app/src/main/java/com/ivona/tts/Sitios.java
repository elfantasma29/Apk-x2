package com.ivona.tts;

import java.util.HashMap;

final class Sitios {

    private static final String[] LISTA = {
            "youtube.com=YouTube", "youtu.be=YouTube", "music.youtube.com=YouTube Music",
            "facebook.com=Facebook", "fb.com=Facebook", "fb.me=Facebook", "messenger.com=Messenger",
            "instagram.com=Instagram", "instagr.am=Instagram",
            "twitter.com=Twitter", "x.com=X", "t.co=Twitter",
            "whatsapp.com=WhatsApp", "wa.me=WhatsApp", "chat.whatsapp.com=un grupo de WhatsApp",
            "telegram.org=Telegram", "t.me=Telegram", "telegram.me=Telegram",
            "tiktok.com=TikTok", "vm.tiktok.com=TikTok",
            "google.com=Google", "goo.gl=Google", "docs.google.com=Google Docs", "drive.google.com=Google Drive",
            "maps.google.com=Google Maps", "maps.app.goo.gl=Google Maps", "play.google.com=Google Play",
            "meet.google.com=Google Meet", "forms.gle=Google Forms", "forms.google.com=Google Forms",
            "photos.google.com=Google Fotos", "photos.app.goo.gl=Google Fotos", "translate.google.com=Google Traductor",
            "gemini.google.com=Gemini", "gmail.com=Gmail", "mail.google.com=Gmail",
            "outlook.com=Outlook", "live.com=Outlook", "hotmail.com=Hotmail", "yahoo.com=Yahoo",
            "linkedin.com=LinkedIn", "lnkd.in=LinkedIn",
            "reddit.com=Reddit", "pinterest.com=Pinterest", "twitch.tv=Twitch", "snapchat.com=Snapchat",
            "spotify.com=Spotify", "open.spotify.com=Spotify", "spotify.link=Spotify",
            "netflix.com=Netflix", "disneyplus.com=Disney Plus", "primevideo.com=Prime Video", "hbomax.com=HBO Max",
            "amazon.com=Amazon", "amazon.com.mx=Amazon", "amazon.es=Amazon", "amzn.to=Amazon", "a.co=Amazon",
            "mercadolibre.com=Mercado Libre", "mercadolibre.com.mx=Mercado Libre", "mercadolibre.com.ar=Mercado Libre",
            "mercadolibre.com.co=Mercado Libre", "mercadolibre.cl=Mercado Libre", "mercadopago.com=Mercado Pago",
            "aliexpress.com=AliExpress", "ebay.com=eBay", "shein.com=Shein", "temu.com=Temu",
            "wikipedia.org=Wikipedia", "github.com=GitHub", "gitlab.com=GitLab", "stackoverflow.com=Stack Overflow",
            "microsoft.com=Microsoft", "office.com=Office", "onedrive.live.com=OneDrive", "1drv.ms=OneDrive",
            "teams.microsoft.com=Microsoft Teams", "sharepoint.com=SharePoint",
            "apple.com=Apple", "icloud.com=iCloud", "apps.apple.com=App Store", "music.apple.com=Apple Música",
            "dropbox.com=Dropbox", "mega.nz=Mega", "mediafire.com=MediaFire", "wetransfer.com=WeTransfer",
            "zoom.us=Zoom", "skype.com=Skype", "discord.com=Discord", "discord.gg=Discord", "slack.com=Slack",
            "paypal.com=PayPal", "paypal.me=PayPal",
            "bit.ly=Bitly", "tinyurl.com=TinyURL", "cutt.ly=Cuttly", "linktr.ee=Linktree",
            "medium.com=Medium", "wordpress.com=WordPress", "blogspot.com=Blogger", "notion.so=Notion",
            "canva.com=Canva", "vimeo.com=Vimeo", "soundcloud.com=SoundCloud", "deezer.com=Deezer",
            "bing.com=Bing", "duckduckgo.com=DuckDuckGo", "openai.com=OpenAI", "chatgpt.com=ChatGPT",
            "claude.ai=Claude", "anthropic.com=Anthropic",
            "steampowered.com=Steam", "epicgames.com=Epic Games", "playstation.com=PlayStation", "xbox.com=Xbox",
            "booking.com=Booking", "airbnb.com=Airbnb", "tripadvisor.com=Tripadvisor", "uber.com=Uber", "rappi.com=Rappi",
            "bbc.com=la BBC", "cnn.com=CNN", "nytimes.com=The New York Times", "elpais.com=El País",
            "infobae.com=Infobae", "clarin.com=Clarín", "eluniversal.com.mx=El Universal", "milenio.com=Milenio",
            "gob.mx=el gobierno de México", "sat.gob.mx=el SAT", "imss.gob.mx=el IMSS"
    };

    private static final HashMap<String, String> MAPA = new HashMap<>();

    static {
        for (String s : LISTA) {
            int i = s.indexOf('=');
            MAPA.put(s.substring(0, i), s.substring(i + 1));
        }
    }

    private Sitios() {
    }

    static int total() {
        return MAPA.size();
    }

    static String buscar(String host) {
        String h = host;
        while (h.length() > 0) {
            String n = MAPA.get(h);
            if (n != null) {
                return n;
            }
            int i = h.indexOf('.');
            if (i < 0) {
                break;
            }
            h = h.substring(i + 1);
        }
        return null;
    }
}
