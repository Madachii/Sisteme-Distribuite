# Cerințe

DistFS este un sistem de fișiere distribuit: un coordonator ține metadatele, nodurile de stocare țin octeții, iar fiecare fișier are 2 copii pe noduri diferite. Clientul vorbește cu coordonatorul pentru metadate și direct cu nodurile pentru date.

## 1. Operații

Operațiile pe care le poate face un utilizator din client. Pașii interni (alocare, replicare, confirmare) sunt descriși în secțiunea 4.

| Operație | Comanda | Ce obține utilizatorul |
| --- | --- | --- |
| Încărcare | `put <name> <path>` | Fișierul local de la `<path>` este salvat în sistem sub numele `<name>`, în 2 copii. |
| Descărcare | `get <name\|id> <path>` | Fișierul este salvat local la `<path>`, identic cu cel încărcat. |
| Localizare | `locate <name\|id>` | Detaliile fișierului (ID, dimensiune, checksum) și nodurile care îl dețin. |
| Listare | `list [limit] [offset]` | Fișierele din sistem, câte `limit` pe pagină (implicit 100, de la 0). |
| Ștergere | `del <name\|id>` | Fișierul nu mai există în sistem. |
| Descoperire | `discover` | Nodurile din cluster, cu stare (`ACTIV`/`SUSPECT`/`CAZUT`) și spațiu liber. |

## 2. Modelul metadatelor

### Fișier

| Câmp | Tip | Observații |
| --- | --- | --- |
| `id` | UUID v7 | Generat la `ALLOC`; ordonat după timp, ca ID-urile de nod. |
| `name` | string UTF-8 | Unic în sistem; fără spații și fără `/`. |
| `size` | long | Dimensiunea în octeți. |
| `checksum` | SHA-256, hex | Calculat de client înainte de `PUT`, verificat de fiecare nod și la `GET`. |
| `replicas` | listă de ID-uri de nod | Exact 2 noduri distincte. |
| `status` | `PENDING` / `COMMITTED` | `PENDING` după `ALLOC`, `COMMITTED` după `COMMIT`. |
| `createdAt` | timestamp | Momentul `ALLOC`. |

Un fișier `PENDING` care nu primește `COMMIT` în 60 s este șters. Doar fișierele `COMMITTED` apar la `LIST` și `LOCATE`.

### Nod 

| Câmp | Tip | Observații |
| --- | --- | --- |
| `id` | UUID v7 | Generat la pornirea nodului (`NodeInfo`). |
| `host`, `tcpPort` | string, int | Adresa la care nodul primește conexiuni TCP. |
| `role` | `NODE` / `COORDINATOR` | Setat cu `--role` (implicit `NODE`). |
| `freeBytes` | long | Spațiul liber raportat în ultimul heartbeat. |
| `lastSeen` | timestamp | Momentul ultimului heartbeat primit. |
| `health` | `ACTIV` / `SUSPECT` / `CAZUT` | `SUSPECT` dacă `lastSeen` e mai vechi de `--suspect` ms (implicit 4000), `CAZUT` dacă e mai vechi de `--fail` ms (implicit 6000). Orice heartbeat nou îl readuce `ACTIV`. |

### Stocare pe disc

Fiecare nod scrie fișierele în `data/<nodeId>/<fileId>`. Metadatele de la coordonator sunt ținute în memorie.

## 3. Cerințe nefuncționale

| Cerință | Obiectiv măsurabil | Cum se verifică |
| --- | --- | --- |
| Toleranță la defecte | `GET` reușește cu oricare un nod de stocare oprit. | În demo se oprește un nod care deține fișierul, apoi se face `get`. |
| Detectarea defectelor | Un nod oprit apare `CAZUT` la `discover` în maxim 6 s. | Se cronometrează de la Ctrl+C pe nod până la schimbarea stării. |
| Integritate | 100% din fișierele descărcate au același SHA-256 ca originalul. | Clientul compară checksum-ul; se verifică și cu `sha256sum`. |
| Performanță la transfer | `put` + `get` pentru un fișier de 10 MB pe localhost în sub 2 s fiecare. | Se măsoară timpul comenzilor în client. |
| Performanță la metadate | `list` cu 1000 de fișiere răspunde în sub 200 ms. | Se încarcă 1000 de fișiere mici și se măsoară `list`. |
| Scalare | Minim 5 noduri pornite pe aceeași mașină, pe porturi diferite. | `scripts/start-local.sh` pornește 5 noduri. |
| Consistență | Un fișier apare la `LIST` doar după ce ambele copii sunt scrise. | `COMMIT` se trimite doar după `STORED` de la al doilea nod. |

**În afara scopului:** coordonatorul este punct unic de defect; dacă se oprește, metadatele se pierd. Nu există autentificare sau criptare.

## 4. Protocol

### TCP (client ↔ coordonator, client ↔ nod, nod ↔ nod)

Fiecare cerere este o linie de text terminată cu `\n`: numele mesajului, apoi câmpurile separate prin spațiu. La `PUT`, `REPLICATE` și răspunsul la `GET`, linia este urmată de exact `size` octeți. O conexiune transportă o cerere și un răspuns.

| Mesaj | De la → către | Conținut | Răspuns |
| --- | --- | --- | --- |
| ALLOC | client → coordonator | nume, dimensiune | NODES cu ID-ul fișierului și 2 noduri, sau ERROR |
| PUT | client → nod | ID, nume, dimensiune, checksum, nodul pentru copie + octeții fișierului | STORED sau ERROR |
| REPLICATE | nod → nod | ca PUT, fără nodul următor | STORED sau ERROR |
| COMMIT | nod → coordonator | ID fișier, nodurile care îl dețin | OK |
| LOCATE | client → coordonator | nume sau ID | LOCATIONS cu metadatele, sau NOT_FOUND |
| GET | client → nod | ID fișier | antet cu dimensiunea + octeții, sau NOT_FOUND |
| LIST | client → coordonator | limit (implicit 100), offset | LIST cu metadatele fișierelor, sau ERROR |
| DEL | client → coordonator | nume sau ID | OK sau NOT_FOUND |
| DISCOVER | client → coordonator | — | NODES cu ID, adresă, health, spațiu liber, sau ERROR |

Exemplu de încărcare:

```
client → coordonator   ALLOC poze.zip 10485760
coordonator → client   NODES <fileId> <idA> 127.0.0.1:7001 <idB> 127.0.0.1:7002
client → nod A         PUT <fileId> poze.zip 10485760 <sha256> 127.0.0.1:7002\n<octeți>
nod A → nod B          REPLICATE <fileId> poze.zip 10485760 <sha256>\n<octeți>
nod B → nod A          STORED
nod A → coordonator    COMMIT <fileId> <idA> <idB>
coordonator → nod A    OK
nod A → client         STORED
```

Dacă `REPLICATE` eșuează, nodul A răspunde clientului cu `ERROR` și nu trimite `COMMIT`.

### UDP multicast (nod → toate nodurile)

Toate nodurile, inclusiv coordonatorul, intră în grupul multicast dat cu `--multicast-group`, pe portul dat cu `--udp-port`. Fiecare nod trimite la 2 s un heartbeat:

```
HEARTBEAT <nodeId> <host> <tcpPort> <role> <freeBytes>
```

Fiecare nod ascultă grupul și actualizează tabelul de noduri (membership), pe care îl verifică la fiecare 100 ms. Un nod trece prin trei stări:

- `ACTIV`: heartbeat-urile lui sosesc;
- `SUSPECT`: nu a sosit niciun heartbeat de `--suspect` ms (implicit 4000, adică 2 heartbeat-uri pierdute);
- `CAZUT`: nu a sosit niciun heartbeat de `--fail` ms (implicit 6000, adică 3 heartbeat-uri pierdute).

Orice heartbeat nou readuce nodul în `ACTIV`. Un nod repornit are alt ID, deci un nod `CAZUT` care revine cu același ID nu a căzut de fapt (a fost înghețat sau s-au pierdut pachete), iar declarația a fost greșită. Coordonatorul răspunde la `DISCOVER` din acest tabel și alege la `ALLOC` doar noduri `ACTIV` cu `freeBytes` cel puțin cât dimensiunea fișierului.

## 5. Scenariul demonstrației

1. Se pornesc coordonatorul și 3 noduri cu `scripts/start-local.sh`.
2. În client, `discover` arată cele 3 noduri `ACTIV`.
3. `put raport.pdf ./raport.pdf` încarcă un fișier; `list` îl arată.
4. `locate raport.pdf` arată cele 2 noduri care îl dețin.
5. Se oprește cu Ctrl+C unul dintre aceste noduri; după 4 s `discover` îl arată `SUSPECT`, iar în maxim 6 s `CAZUT`.
6. `get raport.pdf ./copie.pdf` reușește de la nodul rămas; checksum-ul coincide.
7. `put` pentru un fișier nou alege doar noduri `ACTIV`.
8. `del raport.pdf`; `list` nu îl mai arată, iar `locate` întoarce `NOT_FOUND`.
