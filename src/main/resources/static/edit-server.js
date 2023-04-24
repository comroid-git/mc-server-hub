let form;
let userSelector;
let permsSelector;

async function sendEditForm(formData) {
    let dto = {};
    for (let key in formData.keys())
        dto[key] = formData.get(key);
    let r = await fetch(form.action, {
        method: 'POST',
        body: JSON.stringify(dto)
    })
    if (r.ok)
        window.location.href = '/server/' + dto.id;
    else alert(r.status + ' Something went wrong :(\n' + r.body);
}

async function submitForm() {
    let formData = new FormData(form);

    formData.set("userPermissions", perms);
    formData.delete('select-user');
    formData.delete('select-perms');

    await sendEditForm(formData); // submit form data
    event.preventDefault(); // do not use default submit
}

function selectUser() {
    // update perms box
    let usr = Array.from(userSelector.options)
        .filter(option => option.selected)
        .map(option => option.value)[0];
    let mask = perms[usr];
    for (let option of permsSelector.options)
        option.selected = (option.value & mask) !== 0;
}

function selectPerms() {
    // update perms dto
    let usr = Array.from(userSelector.options)
        .filter(option => option.selected)
        .map(option => option.value)[0];
    perms[usr] = Array.from(permsSelector.options)
        .filter(option => option.selected)
        .map(option => option.value)
        .reduce((x, y) => x | y, 0);
}

function addUser() {
    let option = document.createElement("option");
    let userNameContainer = document.getElementById('add-user');
    var userId = option.value = userNameContainer.textContent;
    userNameContainer.validity = userId.match('^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}$');
    option.innerText = 'UserId:' + userId;
    userSelector.innerHTML += option;
}

function init() {
    form = document.getElementById("editForm");
    userSelector = document.getElementById("select-user");
    permsSelector = document.getElementById("select-perms");

    form.addEventListener("submit", submitForm);

    for (let key in Object.keys(perms)) {
        let option = document.createElement("option");
        option.value = key;
        option.innerText = 'UserId:' + key;
        userSelector.innerHTML += option;
    }

    selectPerms();
}
