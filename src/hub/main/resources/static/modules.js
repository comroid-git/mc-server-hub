$(document).ready(()=>{
    document.querySelectorAll('.state-switch').forEach(checkbox => checkbox.addEventListener('click', switchModuleState))
    document.querySelectorAll('.ui-table-parent').forEach(header => header.addEventListener('click', toggleExpansion))
});

function addModule() {
}

function switchModuleState(event) {
    let id = event.target.id.substring('state_'.length);
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

function toggleExpansion(event) {
    event.stopPropagation();
    let id = event.target.parentElement.id.substring('module_'.length);
    $('#detail_'+id).slideToggle(1000, ()=>{});
}
