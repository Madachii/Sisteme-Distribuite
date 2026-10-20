# Cerințe

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
