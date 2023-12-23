function load() {
    document.querySelectorAll('.state-switch').forEach(checkbox => checkbox.addEventListener('click', switchModuleState))
}

function unload() {
}

function addModule() {
}

function switchModuleState(event) {
    var id = event.target.id.substring('state_'.length);
    fetch('/api/webapp/module/state/'+id+'?toggle=true')
        .then(rsp => rsp.json())
        .then(state => document.querySelector('#state_'+id).checked = state)
        .catch(console.error);
}

function reload(full) {
    let id = document.querySelector('[name=id]').value;
    fetch('/api/webapp/module/state/'+id+'?fullReload='+full)
        .catch(console.error);
}
