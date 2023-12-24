$(document).ready(()=> {
    refreshServerList();
});

function refreshServerList() {
    document.querySelectorAll('.serverEntry').forEach(entry => {
        entry.querySelector('.statusIcon').className = 'statusIcon icon-loading';
        entry.querySelector('.motd').innerHTML = 'Fetching MOTD ...';
        entry.querySelector('.players').innerHTML = 'Fetching players ...';
        fetch('/api/webapp/server/'+entry.id+'/status')
            .then(resp => {
                if (resp.status !== 200)
                    console.error('status request was not successful')
                return resp;
            })
            .then(resp => resp.json())
            .then(data => {
                entry.querySelector('.statusIcon').className = 'statusIcon icon-'+data.status;
                entry.querySelector('.motd').innerHTML = data.status==='offline'?'---':data.motd;
                entry.querySelector('.players').innerHTML = data.status==='offline'?'---':`${data.playerCount}/${data.playerMax}`;
            })
            .catch(error => console.log('could not update status of '+entry.id, error))
    })
}
