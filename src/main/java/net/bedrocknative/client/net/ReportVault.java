package net.bedrocknative.client.net;

// NOTE (analyse RE) : la version d'origine embarquait l'URL d'un webhook Discord chiffree en AES-GCM
// (telemetrie cachee : pseudo + gamertag + serveur). Le materiel chiffre a ete RETIRE de cette copie.
// url() renvoie null -> l'envoi dans ClientReport est neutralise.
final class ReportVault {
   private ReportVault() {
   }

   static String url() {
      return null;
   }
}
