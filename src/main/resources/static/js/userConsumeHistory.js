$(document).ready(function () {
    var chargeList;
    getChargeList();
    getTicketList();
    function getChargeList() {
        getRequest(
            '/vip/getChargeRecord?id='+parseInt(sessionStorage.getItem("id")),
            function (res) {
                console.log(res);
                renderChargeList(res.content);
            },
            function (error) {
                alert(error);
            }
        )
    }

    function renderChargeList(chargeList) {
        for (var i=0;i<chargeList.length;i++) {
            console.log(chargeList[i]);
            var chargeTime = chargeList[i].time;
            var chargeAmount = chargeList[i].amount;
            var cardBalance = chargeList[i].balance;
            var chargetex = "";

            chargetex+= "<tr><td>"+chargeTime+"</td>"+
                "<td>"+chargeAmount+"</td>"+
                "<td>"+cardBalance+"</td></tr>";
            $('#charge-list-body').append(chargetex);
        }
    }

    function getTicketList() {
        getRequest(
            '/user/member/getConsumption?id='+parseInt(sessionStorage.getItem("id")),
            function (res) {
                renderTicketList(res.content);
            }
        )
    }
    function renderTicketList(ticketList) {
        console.log(ticketList);
        for (var j = 0; j < ticketList.length; j++) {
            var scheduleItem=ticketList[j].schedule;
            var movieName = scheduleItem.movieName;
            var startTime=scheduleItem.startTime;
            var endTime=scheduleItem.endTime;
            var bodyContent="";

            bodyContent+="<tr><td>"+movieName+"</td>"+
                "<td>"+startTime+"</td>"+
                "<td>"+endTime+"</td>"+
                "<button>"+"查看详情"+"</button>"

            $('ticket-list-body').append(bodyContent);
        }
    }
})