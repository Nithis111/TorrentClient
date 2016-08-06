<script type="text/javascript">
	HTMLFormElement.prototype._submit = HTMLFormElement.prototype.submit;
	HTMLFormElement.prototype.submit = interceptor;

	window.addEventListener('submit', function(e) {
		interceptor(e);
	}, true);

	function interceptor(e) {
		var f = e ? e.target : this;

		var form = [];
		for (i = 0; i < f.elements.length; i++) {
			var name = f.elements[i].name;
			var value = f.elements[i].value;
			form.push({name, value});
		}
		interception.customSubmit(
				f.attributes['method'] === undefined ? null
						: f.attributes['method'].nodeValue,
				f.attributes['action'] === undefined ? null
						: f.attributes['action'].nodeValue,
                JSON.stringify({form}));
	}

	lastXmlhttpRequestPrototypeCall = null;
	XMLHttpRequest.prototype.reallyOpen = XMLHttpRequest.prototype.open;
	XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
		lastXmlhttpRequestPrototypeCall = {method, url, async, user, password};
		this.reallyOpen(method, url, async, user, password);
	};
	XMLHttpRequest.prototype.reallySend = XMLHttpRequest.prototype.send;
	XMLHttpRequest.prototype.send = function(body) {
		interception.customAjax(JSON.stringify(lastXmlhttpRequestPrototypeCall), body);
		lastXmlhttpRequestPrototypeMethod = null;
		this.reallySend(body);
	};
</script>
