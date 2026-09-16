(() => {
  const exact = new Map([
    ['How to buy', 'Qanday sotib olish'],
    ['Universities', 'Universitetlar'],
    ['Buy an essay', 'Esse sotib olish'],
    ['Download Phentist app', 'Phentist ilovasini yuklab olish'],
    ['Download Android app', 'Android ilovasini yuklab olish'],
    ['Download Phentist for Android', 'Phentist’ni Android uchun yuklab olish'],
    ['Back to landing', 'Bosh sahifaga qaytish'],
    ['University Gallery', 'Universitetlar galereyasi'],
    ['Browse universities and their essay questions.', 'Universitetlar va ularning esse savollarini ko‘rib chiqing.'],
    ['Questions worth studying', 'O‘rganishga arziydigan savollar'],
    ['Read the prompt and preview. Purchase is completed only in the Android app.', 'Savol va qisqa namunani ko‘ring. Xarid faqat Android ilovasida amalga oshiriladi.'],
    ['Study on web. Unlock in the app.', 'Saytda tanlang. Ilovada oching.'],
    ['Phentist keeps purchases and protected reading inside the Android application. The web version is your catalog and learning landing page.', 'Phentist xarid va himoyalangan o‘qishni Android ilovasida amalga oshiradi. Veb-versiya esa katalog va tanishuv sahifasi sifatida xizmat qiladi.'],
    ['Continue to /buy', '/buy sahifasiga o‘tish'],
    ['Full responses stay protected in the mobile app.', 'To‘liq javoblar mobil ilovada himoyalangan.'],
    ['No universities have been published yet.', 'Hozircha universitetlar e’lon qilinmagan.'],
    ['Essay questions will appear here when the catalog is published.', 'Katalog e’lon qilinganda esse savollari shu yerda ko‘rinadi.'],
    ['PHENTIST / BUY', 'PHENTIST / XARID'],
    ['The web catalog never completes the purchase. Open the Android app to buy.', 'Veb-katalogda xarid amalga oshirilmaydi. Xarid qilish uchun Android ilovasini oching.'],
    ['CATALOG', 'KATALOG'],
    ['Choose an essay', 'Esse tanlang'],
    ['University essays', 'Universitet esselari'],
    ['No essay questions are available for this selection.', 'Bu tanlov uchun esse savollari mavjud emas.'],
    ['Why mobile only?', 'Nega faqat mobil ilovada?'],
    ['Purchases are completed only in the Android app.', 'Xarid faqat Android ilovasida amalga oshiriladi.'],
    ['WHAT YOU CAN LEARN', 'NIMALARNI O‘RGANISHINGIZ MUMKIN'],
    ['Study the prompt, structure, tone and approach shown in the protected response.', 'Himoyalangan javobdagi savol, tuzilma, uslub va yondashuvni o‘rganing.'],
    ['Buy in app', 'Ilovada sotib olish'],
    ['ESSAY QUESTION', 'ESSE SAVOLI'],
    ['MOBILE PURCHASE', 'MOBIL XARID'],
    ['DISCOVER', 'KASHF ETING'],
    ['ESSAYS', 'ESSELAR'],
    ['Administration', 'Boshqaruv paneli'],
    ['Landing', 'Bosh sahifa'],
    ['Sign out', 'Chiqish'],
    ['Google sign-in is required. Only the configured Phentist administrator can enter.', 'Google orqali kirish talab qilinadi. Faqat Phentist uchun belgilangan administrator kira oladi.'],
    ['This Google account is not the Phentist administrator.', 'Bu Google hisobi Phentist administratori hisoblanmaydi.'],
    ['Control center', 'Boshqaruv markazi'],
    ['Changes are saved to the database and reflected on the web catalog.', 'O‘zgarishlar ma’lumotlar bazasiga saqlanadi va veb-katalogda aks etadi.'],
    ['Website', 'Veb-sayt'],
    ['Universities', 'Universitetlar'],
    ['Essays', 'Esselar'],
    ['WEBSITE CONTENT', 'SAYT MAZMUNI'],
    ['Landing & /buy', 'Bosh sahifa va /buy'],
    ['Edit copy, app download destination and purchase contexts without changing code.', 'Kodga tegmasdan matnlar, ilovani yuklab olish manzili va xarid bo‘yicha bo‘limlarni o‘zgartiring.'],
    ['Hero eyebrow', 'Asosiy kichik sarlavha'],
    ['Hero title', 'Asosiy sarlavha'],
    ['Hero subtitle', 'Asosiy izoh'],
    ['Android app download URL', 'Android ilovasini yuklab olish havolasi'],
    ['Buy page title', 'Xarid sahifasi sarlavhasi'],
    ['Support email', 'Qo‘llab-quvvatlash elektron pochtasi'],
    ['Buy page subtitle', 'Xarid sahifasi izohi'],
    ['Purchase context 1', 'Xarid bo‘yicha 1-qadam'],
    ['Purchase context 2', 'Xarid bo‘yicha 2-qadam'],
    ['Purchase context 3', 'Xarid bo‘yicha 3-qadam'],
    ['Why mobile only', 'Nega faqat mobil ilovada'],
    ['Footer text', 'Pastki qism matni'],
    ['Save changes', 'O‘zgarishlarni saqlash'],
    ['WEBSITE', 'VEB-SAYT'],
    ['UNIVERSITY MANAGEMENT', 'UNIVERSITETLARNI BOSHQARISH'],
    ['Add university', 'Universitet qo‘shish'],
    ['University name', 'Universitet nomi'],
    ['Location', 'Joylashuv'],
    ['Image URL', 'Rasm havolasi'],
    ['Add', 'Qo‘shish'],
    ['Remove', 'O‘chirish'],
    ['ESSAY MANAGEMENT', 'ESSELERNI BOSHQARISH'],
    ['Select university', 'Universitetni tanlang'],
    ['Question / prompt', 'Savol / topshiriq'],
    ['Word limit', 'So‘zlar limiti'],
    ['Price (UZS)', 'Narx (UZS)'],
    ['Preview text', 'Qisqa namuna'],
    ['Full essay response', 'To‘liq esse javobi'],
    ['Author name', 'Muallif ismi'],
    ['Author avatar URL', 'Muallif rasmi havolasi'],
    ['Add essay', 'Esse qo‘shish'],
    ['Phentist reading preview', 'Phentist o‘qish namunasi'],
    ['ESSAY', 'ESSE'],
    ['WORDS', 'SO‘Z'],
    ['Preview available in Phentist.', 'Qisqa namuna Phentist’da mavjud.'],
    ['Follow this step in your Phentist journey.', 'Phentist’dan foydalanish jarayonidagi ushbu qadamni bajaring.'],
    ['No essay questions are available for this selection.', 'Bu tanlov uchun esse savollari mavjud emas.'],
    ['Request failed', 'So‘rov bajarilmadi'],
    ['admin_only', 'Faqat administratorlar uchun'],
    ['Saved.', 'Saqlandi.'],
    ['Deleted.', 'O‘chirildi.']
  ]);

  const patterns = [
    [/^(\d+) words · /, '$1 so‘z · '],
    [/^(\d+) WORDS$/, '$1 SO‘Z'],
    [/^(.+) · (\d+) WORDS$/, '$1 · $2 SO‘Z'],
    [/^(.+) · Buy in app$/, '$1 · Ilovada sotib olish']
  ];

  function translateText(value) {
    if (!value || !value.trim()) return value;
    const trimmed = value.trim();
    if (exact.has(trimmed)) return value.replace(trimmed, exact.get(trimmed));
    for (const [re, replacement] of patterns) {
      if (re.test(trimmed)) return value.replace(trimmed, trimmed.replace(re, replacement));
    }
    return value;
  }

  function translate(root = document.body) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    for (const node of nodes) {
      if (node.parentElement?.closest('script,style,textarea,input')) continue;
      const next = translateText(node.nodeValue || '');
      if (next !== node.nodeValue) node.nodeValue = next;
    }
    root.querySelectorAll?.('input[placeholder],textarea[placeholder],input[aria-label],button[aria-label]').forEach(el => {
      for (const attr of ['placeholder','aria-label']) {
        if (el.hasAttribute(attr)) el.setAttribute(attr, translateText(el.getAttribute(attr) || ''));
      }
    });
  }

  const start = () => {
    translate();
    new MutationObserver(() => translate()).observe(document.body, {childList:true, subtree:true});
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, {once:true});
  else start();
})();
